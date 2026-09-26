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

variable "az_count" {
  description = "Number of availability zones to spread subnets across. An ALB and an RDS subnet group each require at least 2."
  type        = number
  default     = 2
}
