resource "aws_db_subnet_group" "main" {
  name       = "${var.project}-db"
  subnet_ids = aws_subnet.private[*].id

  tags = {
    Name = "${var.project}-db-subnet-group"
  }
}

resource "aws_db_instance" "main" {
  identifier = "${var.project}-db"

  engine         = "postgres"
  engine_version = "16.15"
  instance_class = var.db_instance_class

  allocated_storage = 20 # RDS's practical minimum for gp3 anyway
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username

  # RDS generates and owns the master password itself, stored as a Secrets
  # Manager secret -- Terraform (and this file) never sees the plaintext
  # value. The generated secret's ARN is exposed as an output below for the
  # ECS task definition (next slice) to reference directly.
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false

  multi_az = false # single-AZ -- budget-conscious, per the agreed posture

  # No automated backups: this environment is meant to be applied for a
  # session and destroyed afterward, not kept running -- a backup of data
  # that's about to be deleted anyway has no value, and skipping it avoids
  # the backup storage cost.
  backup_retention_period = 0

  skip_final_snapshot = true # otherwise `terraform destroy` fails demanding one
  deletion_protection = false
  apply_immediately   = true

  tags = {
    Name = "${var.project}-db"
  }
}
