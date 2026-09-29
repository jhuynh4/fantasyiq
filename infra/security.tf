# Security groups are the firewall around each piece of the stack. The rule
# is "least privilege by reference": each layer only accepts traffic from
# the layer directly in front of it, identified by security group rather
# than by IP range.
#
#   internet --80--> [alb] --8080--> [app] --5432--> [db]
#                                       \---6379---> [redis]
#
# Rules are separate resources (aws_vpc_security_group_*_rule) rather than
# inline blocks -- the provider's recommended style, and it avoids a
# dependency cycle between the alb and app groups, which reference each other.

resource "aws_security_group" "alb" {
  name        = "${var.project}-alb"
  description = "Load balancer: HTTP in from the internet, traffic out to the app only"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${var.project}-alb-sg"
  }
}

resource "aws_security_group" "app" {
  name        = "${var.project}-app"
  description = "App tasks: traffic in from the load balancer only"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${var.project}-app-sg"
  }
}

resource "aws_security_group" "db" {
  name        = "${var.project}-db"
  description = "Postgres: traffic in from the app only"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${var.project}-db-sg"
  }
}

resource "aws_security_group" "redis" {
  name        = "${var.project}-redis"
  description = "Redis: traffic in from the app only"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${var.project}-redis-sg"
  }
}

# --- Load balancer ---------------------------------------------------------

# Plain HTTP only for now. HTTPS needs a domain name and an ACM certificate,
# which this project doesn't have yet -- worth adding before real users,
# not needed to learn the deployment mechanics.
resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  security_group_id = aws_security_group.alb.id
  description       = "HTTP from anywhere"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_app" {
  security_group_id            = aws_security_group.alb.id
  description                  = "To the app tasks"
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = var.app_port
  to_port                      = var.app_port
  ip_protocol                  = "tcp"
}

# --- App -------------------------------------------------------------------

resource "aws_vpc_security_group_ingress_rule" "app_from_alb" {
  security_group_id            = aws_security_group.app.id
  description                  = "From the load balancer"
  referenced_security_group_id = aws_security_group.alb.id
  from_port                    = var.app_port
  to_port                      = var.app_port
  ip_protocol                  = "tcp"
}

# The app has to reach ESPN, OpenWeatherMap, The Odds API, ECR, Secrets
# Manager, CloudWatch, and its own database/cache, so outbound is open.
# Inbound is what actually needs to be locked down.
resource "aws_vpc_security_group_egress_rule" "app_all_out" {
  security_group_id = aws_security_group.app.id
  description       = "All outbound"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# --- Database and cache ----------------------------------------------------

resource "aws_vpc_security_group_ingress_rule" "db_from_app" {
  security_group_id            = aws_security_group.db.id
  description                  = "Postgres from the app"
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "redis_from_app" {
  security_group_id            = aws_security_group.redis.id
  description                  = "Redis from the app"
  referenced_security_group_id = aws_security_group.app.id
  from_port                    = 6379
  to_port                      = 6379
  ip_protocol                  = "tcp"
}
