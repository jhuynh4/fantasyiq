resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${var.project}"
  retention_in_days = 7 # short retention -- keeps log storage cost near zero for a learning environment
}

resource "aws_ecs_cluster" "main" {
  name = var.project
}

# Reuses exactly the env-var-override pattern already verified when testing
# the Dockerfile locally against docker-compose's Postgres/Redis
# (SPRING_DATASOURCE_URL / SPRING_DATA_REDIS_HOST env vars override
# application-local.yml's hardcoded local values) -- same mechanism, now
# pointed at RDS/ElastiCache instead of local containers. SPRING_PROFILES_ACTIVE
# stays "local" rather than introducing a new profile for this first slice;
# the local profile's DEBUG-level logging is noisier than you'd want for
# real production traffic, a known simplification worth revisiting if this
# ever serves real users instead of being a learning/demo deployment.
resource "aws_ecs_task_definition" "app" {
  family                   = var.project
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  # 0.5 vCPU / 1GB, not Fargate's absolute minimum (0.25/0.5) -- a JVM's
  # default heap sizing (a fraction of container memory) makes the smallest
  # size a real risk of an OOM crash loop on first deploy, and the cost
  # difference is about a cent per hour. Worth revisiting downward once the
  # app's actual memory footprint under this workload is known.
  cpu                = "512"
  memory             = "1024"
  execution_role_arn = aws_iam_role.ecs_task_execution.arn

  container_definitions = jsonencode([
    {
      name      = "app"
      image     = "${aws_ecr_repository.app.repository_url}:${var.app_image_tag}"
      essential = true

      portMappings = [
        {
          containerPort = var.app_port
          protocol      = "tcp"
        }
      ]

      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "local" },
        { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${aws_db_instance.main.endpoint}/${var.db_name}" },
        { name = "SPRING_DATA_REDIS_HOST", value = aws_elasticache_cluster.main.cache_nodes[0].address },
      ]

      # RDS's own secret stores {"username":"...","password":"..."} as one
      # JSON blob -- the ":password::" suffix tells Secrets Manager which
      # key to extract (the trailing empty segments are the optional
      # version-stage/version-id AWS's ARN format reserves). The other
      # three secrets are plain strings, so no key suffix is needed.
      secrets = [
        { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${aws_db_instance.main.master_user_secret[0].secret_arn}:password::" },
        { name = "JWT_SECRET", valueFrom = aws_secretsmanager_secret.jwt_secret.arn },
        { name = "OPENWEATHER_API_KEY", valueFrom = aws_secretsmanager_secret.openweather_api_key.arn },
        { name = "ODDS_API_KEY", valueFrom = aws_secretsmanager_secret.odds_api_key.arn },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.app.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "app"
        }
      }
    }
  ])

  tags = {
    Name = "${var.project}-task"
  }
}

resource "aws_ecs_service" "app" {
  name            = var.project
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = true # public subnet, no NAT gateway -- see network.tf
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "app"
    container_port   = var.app_port
  }

  # Without this, Terraform could create the service before the listener
  # exists to route traffic to it -- not fatal (ECS would just retry failed
  # health checks until the listener catches up), but a cleaner apply order.
  depends_on = [aws_lb_listener.http]

  tags = {
    Name = "${var.project}-service"
  }
}
