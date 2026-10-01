# Lets GitHub Actions request short-lived AWS credentials directly at
# workflow run time, instead of storing a long-lived AWS access key as a
# GitHub secret -- nothing here is a credential that could leak from a
# compromised secret or a misconfigured log line. One provider resource
# covers every repo/workflow in this AWS account that trusts GitHub's
# OIDC issuer; the trust conditions on the role below are what actually
# restrict who can use it.
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
  # GitHub's well-known OIDC thumbprint. AWS has relaxed strict validation
  # of this field for recognized providers like GitHub, but the API still
  # requires a syntactically valid value.
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

data "aws_iam_policy_document" "github_actions_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # Scoped to this exact repo and only the main branch -- a workflow run
    # from a fork, a feature branch, or a pull_request event can't assume
    # this role, only a push that landed on main.
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repo}:ref:refs/heads/main"]
    }
  }
}

resource "aws_iam_role" "github_actions_deploy" {
  name               = "${var.project}-github-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume.json
}

data "aws_iam_policy_document" "github_actions_deploy_permissions" {
  # ecr:GetAuthorizationToken doesn't support resource-level scoping --
  # AWS requires "*" for this specific action regardless of how narrowly
  # you'd like to scope it.
  statement {
    sid       = "EcrAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "PushImageToThisRepository"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:PutImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
    ]
    resources = [aws_ecr_repository.app.arn]
  }

  # RegisterTaskDefinition/DescribeTaskDefinition are two more ECS actions
  # that don't support resource-level scoping (a known ECS IAM quirk --
  # confirmed against AWS's own documented action list, not a shortcut).
  statement {
    sid       = "RegisterNewTaskDefinitionRevision"
    actions   = ["ecs:RegisterTaskDefinition", "ecs:DescribeTaskDefinition"]
    resources = ["*"]
  }

  statement {
    sid       = "UpdateTheRunningService"
    actions   = ["ecs:UpdateService", "ecs:DescribeServices"]
    resources = [aws_ecs_service.app.id]
  }

  # Registering a task definition that references the execution role
  # requires explicit permission to hand that role to ECS -- without this,
  # RegisterTaskDefinition fails even though the role itself is unchanged.
  statement {
    sid       = "PassExecutionRoleToEcs"
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.ecs_task_execution.arn]
  }
}

resource "aws_iam_role_policy" "github_actions_deploy_permissions" {
  name   = "${var.project}-github-deploy-permissions"
  role   = aws_iam_role.github_actions_deploy.id
  policy = data.aws_iam_policy_document.github_actions_deploy_permissions.json
}
