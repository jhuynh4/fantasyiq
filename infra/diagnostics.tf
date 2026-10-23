# Opt-in request tracing, for chasing a slow or stalled request to the leg that lost the time.
# Everything here exists only when var.enable_diagnostics is true (apply with
# `terraform apply "-var=enable_diagnostics=true"`); with the default, nothing in this file is created.
#
# A request crosses four legs, and each log below measures one of them:
#
#   viewer -> CloudFront -> load balancer -> app
#
#   CloudFront real-time log   time-to-first-byte, origin-fbl (first-byte latency between CloudFront
#                              and the origin), x-edge-detailed-result-type (OriginConnectError etc.)
#   ALB access log             request_processing_time (the load balancer's wait to reach a target,
#                              -1 when it could not connect within its 10-second timeout),
#                              target_processing_time (the app), request_creation_time
#   VPC flow log (REJECT only) connection attempts the security groups dropped, with the source address
#
# CloudFront's standard (non-real-time) logs are deliberately not used: AWS delivers them up to an hour
# late and says they are only reliable about four hours after enabling, and they have no origin timing.
# Real-time logs arrive within seconds and have origin-fbl / origin-lbl.

locals {
  diagnostics_count = var.enable_diagnostics ? 1 : 0
  # The real-time log needs a Kinesis stream, and an account that has not been subscribed to Kinesis
  # cannot create one (SubscriptionRequiredException); this switch leaves just that part out.
  realtime_log_count = var.enable_diagnostics && var.enable_cloudfront_realtime_log ? 1 : 0
}

resource "aws_s3_bucket" "diagnostics" {
  count = local.diagnostics_count

  bucket        = "${var.project}-diagnostics-${data.aws_caller_identity.current.account_id}"
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "diagnostics" {
  count = local.diagnostics_count

  bucket                  = aws_s3_bucket.diagnostics[0].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Logs are only useful for the session that produced them.
resource "aws_s3_bucket_lifecycle_configuration" "diagnostics" {
  count = local.diagnostics_count

  bucket = aws_s3_bucket.diagnostics[0].id

  rule {
    id     = "expire-logs"
    status = "Enabled"

    filter {}

    expiration {
      days = 7
    }
  }
}

# The account Elastic Load Balancing writes from in this region (us-east-1 uses the legacy account
# principal, not a service principal).
data "aws_elb_service_account" "main" {}

data "aws_iam_policy_document" "diagnostics_bucket" {
  count = local.diagnostics_count

  statement {
    sid       = "AlbWritesAccessLogs"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.diagnostics[0].arn}/alb/AWSLogs/${data.aws_caller_identity.current.account_id}/*"]

    principals {
      type        = "AWS"
      identifiers = [data.aws_elb_service_account.main.arn]
    }
  }

  statement {
    sid       = "FlowLogsWrite"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.diagnostics[0].arn}/flow/AWSLogs/${data.aws_caller_identity.current.account_id}/*"]

    principals {
      type        = "Service"
      identifiers = ["delivery.logs.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "s3:x-amz-acl"
      values   = ["bucket-owner-full-control"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }

  statement {
    sid       = "FlowLogsCheckBucket"
    actions   = ["s3:GetBucketAcl"]
    resources = [aws_s3_bucket.diagnostics[0].arn]

    principals {
      type        = "Service"
      identifiers = ["delivery.logs.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_s3_bucket_policy" "diagnostics" {
  count = local.diagnostics_count

  bucket = aws_s3_bucket.diagnostics[0].id
  policy = data.aws_iam_policy_document.diagnostics_bucket[0].json

  depends_on = [aws_s3_bucket_public_access_block.diagnostics]
}

# Only connection attempts that were rejected: a stall caused by a security group dropping some
# CloudFront addresses shows up here as REJECT records for the load balancer's port 80, with the source.
resource "aws_flow_log" "rejected" {
  count = local.diagnostics_count

  vpc_id                   = aws_vpc.main.id
  traffic_type             = "REJECT"
  log_destination_type     = "s3"
  log_destination          = "${aws_s3_bucket.diagnostics[0].arn}/flow/"
  max_aggregation_interval = 60

  depends_on = [aws_s3_bucket_policy.diagnostics]
}

# --- CloudFront real-time log, to a Kinesis stream -------------------------------------------------

resource "aws_kinesis_stream" "cloudfront" {
  count = local.realtime_log_count

  name             = "${var.project}-cloudfront-realtime"
  shard_count      = 1
  retention_period = 24

  stream_mode_details {
    stream_mode = "PROVISIONED"
  }
}

data "aws_iam_policy_document" "cloudfront_realtime_trust" {
  count = local.realtime_log_count

  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    # Stops another account's distribution from using this role (the confused-deputy problem).
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_iam_role" "cloudfront_realtime" {
  count = local.realtime_log_count

  name               = "${var.project}-cloudfront-realtime-logs"
  assume_role_policy = data.aws_iam_policy_document.cloudfront_realtime_trust[0].json
}

data "aws_iam_policy_document" "cloudfront_realtime_permissions" {
  count = local.realtime_log_count

  statement {
    actions = [
      "kinesis:DescribeStreamSummary",
      "kinesis:DescribeStream",
      "kinesis:PutRecord",
      "kinesis:PutRecords",
    ]
    resources = [aws_kinesis_stream.cloudfront[0].arn]
  }
}

resource "aws_iam_role_policy" "cloudfront_realtime" {
  count = local.realtime_log_count

  name   = "write-to-the-stream"
  role   = aws_iam_role.cloudfront_realtime[0].id
  policy = data.aws_iam_policy_document.cloudfront_realtime_permissions[0].json
}

resource "aws_cloudfront_realtime_log_config" "api" {
  count = local.realtime_log_count

  name          = "${var.project}-api"
  sampling_rate = 100

  # Records arrive as tab-separated values in the order AWS documents the fields, NOT the order of this
  # list, so this list is kept in that documented order. scripts/diagnostics/read_cloudfront_realtime.py
  # assumes exactly this order; change both together.
  fields = [
    "timestamp",
    "c-ip",
    "s-ip",
    "time-to-first-byte",
    "sc-status",
    "cs-method",
    "cs-uri-stem",
    "x-edge-location",
    "x-edge-request-id",
    "time-taken",
    "x-edge-detailed-result-type",
    "origin-fbl",
    "origin-lbl",
  ]

  endpoint {
    stream_type = "Kinesis"

    kinesis_stream_config {
      role_arn   = aws_iam_role.cloudfront_realtime[0].arn
      stream_arn = aws_kinesis_stream.cloudfront[0].arn
    }
  }

  depends_on = [aws_iam_role_policy.cloudfront_realtime]
}
