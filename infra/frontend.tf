data "aws_caller_identity" "current" {}

# The built frontend: plain static files, served only through CloudFront (the
# bucket itself is private). Named with the account id because S3 bucket names
# are global, and the deploy workflow needs a name that doesn't change between
# a `terraform destroy` and the next `apply`.
resource "aws_s3_bucket" "frontend" {
  bucket = "${var.project}-frontend-${data.aws_caller_identity.current.account_id}"

  # Lets `terraform destroy` empty and delete the bucket. The contents are a
  # build artifact that the pipeline recreates on every push to main.
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket                  = aws_s3_bucket.frontend.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Origin access control: CloudFront signs its requests to S3, and the bucket
# policy below lets only this one distribution read. Nothing else can fetch
# an object, including by guessing the bucket's own URL.
resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "${var.project}-frontend"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# Managed policies, looked up by name so no opaque ids are copied in here.
data "aws_cloudfront_cache_policy" "optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_cache_policy" "disabled" {
  name = "Managed-CachingDisabled"
}

# Forwards every header the browser sent (including Authorization) to the
# API, except Host, which CloudFront sets to the origin's own name.
data "aws_cloudfront_origin_request_policy" "all_viewer_except_host" {
  name = "Managed-AllViewerExceptHostHeader"
}

data "aws_cloudfront_response_headers_policy" "security" {
  name = "Managed-SecurityHeadersPolicy"
}

# A single-page app has one real page, index.html, and the router decides what
# to show for /players/<id> and the like. So a request for a path that isn't a
# file is answered with index.html.
#
# This is a function on the app's own behavior instead of the usual
# distribution-wide "turn 403/404 into index.html" error response, because
# that setting also applies to /api/*: a real 404 ("player not found") or 403
# from the backend would come back as the HTML page with a 200.
resource "aws_cloudfront_function" "spa_fallback" {
  name    = "${var.project}-spa-fallback"
  runtime = "cloudfront-js-2.0"
  publish = true
  comment = "Serve index.html for paths that are not files, so client-side routes survive a reload."
  code    = <<-JS
    function handler(event) {
      var request = event.request;
      if (!/\.[A-Za-z0-9]+$/.test(request.uri)) {
        request.uri = '/index.html';
      }
      return request;
    }
  JS
}

resource "aws_cloudfront_distribution" "frontend" {
  # The deploy workflow finds the distribution by this comment, because the id
  # is generated and changes every time the stack is recreated.
  comment             = "${var.project} frontend"
  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"
  # The cheapest tier (North America and Europe edge locations).
  price_class = "PriceClass_100"

  origin {
    origin_id                = "frontend-bucket"
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }

  # The API. CloudFront to the ALB is plain HTTP because the ALB has no
  # certificate yet; the browser-to-CloudFront leg is HTTPS, which is the part
  # that matters for the mixed-content rule.
  origin {
    origin_id   = "api"
    domain_name = aws_lb.main.dns_name

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
      # The trade analyzer takes a few seconds; the default 30s is enough, but
      # a slow cold database shouldn't turn into a gateway error.
      origin_read_timeout = 60
    }
  }

  default_cache_behavior {
    target_origin_id           = "frontend-bucket"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    cache_policy_id            = data.aws_cloudfront_cache_policy.optimized.id
    response_headers_policy_id = data.aws_cloudfront_response_headers_policy.security.id

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_fallback.arn
    }
  }

  ordered_cache_behavior {
    path_pattern             = "/api/*"
    target_origin_id         = "api"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    compress                 = true
    cache_policy_id          = data.aws_cloudfront_cache_policy.disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # The *.cloudfront.net name and its certificate. A custom domain needs a
  # registered name and an ACM certificate in us-east-1, neither of which this
  # project has.
  viewer_certificate {
    cloudfront_default_certificate = true
  }
}

data "aws_iam_policy_document" "frontend_bucket" {
  statement {
    sid       = "ReadFromThisDistributionOnly"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.frontend.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.frontend.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  policy = data.aws_iam_policy_document.frontend_bucket.json
}
