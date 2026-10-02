output "vpc_id" {
  value = aws_vpc.main.id
}

output "public_subnet_ids" {
  value = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  value = aws_subnet.private[*].id
}

output "ecr_repository_url" {
  description = "Where to push the Docker image."
  value       = aws_ecr_repository.app.repository_url
}

output "db_endpoint" {
  description = "host:port for SPRING_DATASOURCE_URL."
  value       = aws_db_instance.main.endpoint
}

output "db_master_user_secret_arn" {
  description = "Secrets Manager ARN holding the RDS-generated master password."
  value       = aws_db_instance.main.master_user_secret[0].secret_arn
}

output "redis_endpoint" {
  description = "Host for SPRING_DATA_REDIS_HOST."
  value       = aws_elasticache_cluster.main.cache_nodes[0].address
}

output "jwt_secret_arn" {
  value = aws_secretsmanager_secret.jwt_secret.arn
}

output "openweather_api_key_secret_arn" {
  value = aws_secretsmanager_secret.openweather_api_key.arn
}

output "odds_api_key_secret_arn" {
  value = aws_secretsmanager_secret.odds_api_key.arn
}

output "app_url" {
  description = "The app's public URL, once the ECS service is healthy behind it."
  value       = "http://${aws_lb.main.dns_name}"
}

output "github_actions_role_arn" {
  description = "Referenced directly in .github/workflows/deploy.yml -- stable across applies since it's a deterministic name, not a generated id."
  value       = aws_iam_role.github_actions_deploy.arn
}

output "dashboard_url" {
  description = "Direct link to the CloudWatch dashboard."
  value       = "https://${var.aws_region}.console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards:name=${aws_cloudwatch_dashboard.main.dashboard_name}"
}
