variable "aws_region" {
  description = "AWS region everything is created in."
  type        = string
  default     = "us-east-1"
}

variable "project" {
  description = "Name prefix used for resource names and the Project tag."
  type        = string
  default     = "fantasyiq"
}

variable "vpc_cidr" {
  description = "IP range for the whole VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "app_port" {
  description = "Port the Spring Boot app listens on inside the container."
  type        = number
  default     = 8080
}

variable "az_count" {
  description = "Number of availability zones to spread subnets across. An ALB and an RDS subnet group each require at least 2."
  type        = number
  default     = 2
}

variable "db_instance_class" {
  description = "RDS instance size. t4g (Graviton/ARM) is cheaper than t3 for the same size -- worth it here since nothing in this stack needs x86."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_name" {
  description = "Database name Flyway migrates against -- matches docker-compose's local Postgres."
  type        = string
  default     = "fantasyiq"
}

variable "db_username" {
  description = "Master username. The password is never set here -- manage_master_user_password lets RDS generate and own it in Secrets Manager instead."
  type        = string
  default     = "fantasyiq"
}

variable "redis_node_type" {
  description = "ElastiCache node size."
  type        = string
  default     = "cache.t4g.micro"
}

variable "app_image_tag" {
  description = "Docker image tag in ECR to deploy. Matches the tag already pushed by hand; the CI/CD pipeline manages this differently (tagging with the git commit SHA) once it takes over deploys."
  type        = string
  default     = "manual-1"
}

variable "github_repo_owner" {
  description = "GitHub username/org -- used alongside its numeric id below to build the OIDC trust policy's sub condition."
  type        = string
  default     = "jhuynh4"
}

variable "github_repo_owner_id" {
  description = "GitHub's numeric account id for github_repo_owner -- see github-oidc.tf's comment on why both the name and the numeric id are needed (GitHub's \"immutable subject claims\" format). Found via a real decoded OIDC token (the `repository_owner_id` claim), not GitHub's UI -- there's no simple page that shows this number."
  type        = string
  default     = "113395047"
}

variable "github_repo_name" {
  description = "Repository name (without the owner)."
  type        = string
  default     = "fantasyiq"
}

variable "github_repo_id" {
  description = "GitHub's numeric id for this repository -- see github_repo_owner_id's comment; found the same way (the `repository_id` claim)."
  type        = string
  default     = "1324571292"
}
