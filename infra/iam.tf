# The execution role is what the ECS *agent* uses before your app's code
# ever runs: pulling the image from ECR, writing container logs to
# CloudWatch, and fetching secrets to inject as environment variables. It is
# NOT a role your application code can use to call AWS APIs itself -- the
# app makes no direct AWS calls today, so there's no separate "task role"
# here. Add one later if that changes.
data "aws_iam_policy_document" "ecs_task_execution_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ecs_task_execution" {
  name               = "${var.project}-ecs-task-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_execution_assume.json
}

# AWS-managed policy covering the two most common execution-role needs:
# ecr:GetDownloadUrlForLayer/BatchGetImage (pulling the image) and
# logs:CreateLogStream/PutLogEvents (writing to CloudWatch).
resource "aws_iam_role_policy_attachment" "ecs_task_execution_managed" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# The managed policy above doesn't cover Secrets Manager -- scoped to
# exactly the four secrets this task actually reads, not a blanket
# secretsmanager:* grant.
data "aws_iam_policy_document" "ecs_task_execution_secrets" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      aws_db_instance.main.master_user_secret[0].secret_arn,
      aws_secretsmanager_secret.jwt_secret.arn,
      aws_secretsmanager_secret.openweather_api_key.arn,
      aws_secretsmanager_secret.odds_api_key.arn,
    ]
  }
}

resource "aws_iam_role_policy" "ecs_task_execution_secrets" {
  name   = "${var.project}-secrets-access"
  role   = aws_iam_role.ecs_task_execution.id
  policy = data.aws_iam_policy_document.ecs_task_execution_secrets.json
}
