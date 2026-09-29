resource "aws_ecr_repository" "app" {
  name = var.project

  # Tags can't be overwritten once pushed, so a tag always means exactly one
  # image. The deploy pipeline will tag with the git commit SHA, which makes
  # every deployed version traceable to a commit. The catch: pushing the same
  # tag twice is an error -- use a new tag each time when pushing by hand.
  image_tag_mutability = "IMMUTABLE"

  # Lets `terraform destroy` delete the repository even when it still holds
  # images. Without it, destroy fails on a non-empty repo. Fine for a
  # learning environment; you'd think harder about this in production.
  force_delete = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep only the 10 most recent images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 10
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
