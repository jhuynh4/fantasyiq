resource "aws_elasticache_subnet_group" "main" {
  name       = "${var.project}-redis"
  subnet_ids = aws_subnet.private[*].id
}

# A single node, not a replication group -- no HA needed for a learning
# environment that gets destroyed between sessions anyway. Matches
# docker-compose's local single-instance Redis.
resource "aws_elasticache_cluster" "main" {
  cluster_id      = "${var.project}-redis"
  engine          = "redis"
  engine_version  = "7.1"
  node_type       = var.redis_node_type
  num_cache_nodes = 1
  port            = 6379

  subnet_group_name  = aws_elasticache_subnet_group.main.name
  security_group_ids = [aws_security_group.redis.id]

  apply_immediately = true

  tags = {
    Name = "${var.project}-redis"
  }
}
