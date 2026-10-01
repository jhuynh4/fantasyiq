# JWT_SECRET is synthetic -- nothing to protect by keeping it out of
# Terraform, unlike a real vendor credential. Generated once here so it's
# stable across applies (Terraform only regenerates it if this resource is
# ever destroyed) and never appears in plan/apply output since it's marked
# sensitive.
resource "random_password" "jwt_secret" {
  length  = 48
  special = false
}

resource "aws_secretsmanager_secret" "jwt_secret" {
  name = "${var.project}/jwt-secret"

  # AWS's default 30-day recovery window means a deleted secret's name
  # stays reserved for 30 days afterward, actively conflicting with this
  # project's destroy-and-recreate-every-session rhythm -- the next
  # `apply` would fail trying to create a secret with the same name while
  # the old one is still "pending deletion". 0 means delete immediately,
  # no recovery window, which is the right tradeoff for a synthetic value
  # Terraform can regenerate on demand anyway.
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "jwt_secret" {
  secret_id     = aws_secretsmanager_secret.jwt_secret.id
  secret_string = random_password.jwt_secret.result
}

# OPENWEATHER_API_KEY / ODDS_API_KEY are real vendor credentials, not
# something Terraform should generate or this file should ever contain.
# These resources only provision the empty container; the real value goes
# in afterward via `aws secretsmanager put-secret-value` run by hand (see
# infra/README.md), the same "you enter it, never through chat" rule
# already used for these same two keys locally. `ignore_changes` on the
# version's value means a real value set out-of-band survives future
# `terraform apply` runs instead of being overwritten back to the
# placeholder.
resource "aws_secretsmanager_secret" "openweather_api_key" {
  name                    = "${var.project}/openweather-api-key"
  recovery_window_in_days = 0 # see jwt_secret's comment above -- same reasoning
}

resource "aws_secretsmanager_secret_version" "openweather_api_key" {
  secret_id     = aws_secretsmanager_secret.openweather_api_key.id
  secret_string = "REPLACE_ME"

  lifecycle {
    ignore_changes = [secret_string]
  }
}

resource "aws_secretsmanager_secret" "odds_api_key" {
  name                    = "${var.project}/odds-api-key"
  recovery_window_in_days = 0 # see jwt_secret's comment above -- same reasoning
}

resource "aws_secretsmanager_secret_version" "odds_api_key" {
  secret_id     = aws_secretsmanager_secret.odds_api_key.id
  secret_string = "REPLACE_ME"

  lifecycle {
    ignore_changes = [secret_string]
  }
}
