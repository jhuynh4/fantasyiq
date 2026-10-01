# One topic for every alarm below -- a single place to subscribe to (or
# later add a Slack/PagerDuty integration to) rather than one topic per
# alarm. alert_email has no default on purpose: it's supplied via
# TF_VAR_alert_email or a gitignored terraform.tfvars, never hardcoded or
# committed, the same "you provide it, never through this code or chat"
# rule already used for the two external API keys.
resource "aws_sns_topic" "alerts" {
  name = "${var.project}-alerts"
}

resource "aws_sns_topic_subscription" "alerts_email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# All four alarms below watch AWS's own native CloudWatch metrics for the
# ALB/ECS/RDS resources already created in alb.tf/ecs.tf/rds.tf -- zero app
# code changes needed, since AWS publishes these automatically for any
# ALB/ECS service/RDS instance that exists. This deliberately does NOT yet
# cover the dev plan's other two alarm targets (ingestion job failure,
# circuit breaker open) -- those live in the app's own Micrometer metrics
# (ingestion.run.duration, resilience4j.circuitbreaker.state), which aren't
# flowing into CloudWatch at all yet. Getting them here needs a new
# micrometer-registry-cloudwatch2 dependency and a new IAM task role (the
# app would need to call CloudWatch's PutMetricData itself) -- a real,
# deliberately separate follow-up slice, not done in this first pass.

resource "aws_cloudwatch_metric_alarm" "alb_5xx" {
  alarm_name          = "${var.project}-alb-5xx"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 300
  statistic           = "Sum"
  threshold           = 5
  alarm_description   = "More than 5 server errors (5xx) from the app in a 5-minute window"
  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
  }
  alarm_actions      = [aws_sns_topic.alerts.arn]
  ok_actions         = [aws_sns_topic.alerts.arn]
  treat_missing_data = "notBreaching"
}

resource "aws_cloudwatch_metric_alarm" "alb_unhealthy_hosts" {
  alarm_name          = "${var.project}-alb-unhealthy-hosts"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "UnHealthyHostCount"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Average"
  threshold           = 0
  alarm_description   = "At least one ECS task is failing its load balancer health check"
  dimensions = {
    TargetGroup  = aws_lb_target_group.app.arn_suffix
    LoadBalancer = aws_lb.main.arn_suffix
  }
  alarm_actions      = [aws_sns_topic.alerts.arn]
  ok_actions         = [aws_sns_topic.alerts.arn]
  treat_missing_data = "notBreaching"
}

resource "aws_cloudwatch_metric_alarm" "ecs_cpu_high" {
  alarm_name          = "${var.project}-ecs-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "ECS task CPU above 80% for 3 consecutive minutes"
  dimensions = {
    ClusterName = aws_ecs_cluster.main.name
    ServiceName = aws_ecs_service.app.name
  }
  alarm_actions      = [aws_sns_topic.alerts.arn]
  treat_missing_data = "notBreaching"
}

resource "aws_cloudwatch_metric_alarm" "rds_cpu_high" {
  alarm_name          = "${var.project}-rds-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CPUUtilization"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "RDS CPU above 80% for 3 consecutive minutes"
  dimensions = {
    DBInstanceIdentifier = aws_db_instance.main.identifier
  }
  alarm_actions      = [aws_sns_topic.alerts.arn]
  treat_missing_data = "notBreaching"
}
