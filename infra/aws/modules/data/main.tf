terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

resource "aws_db_subnet_group" "this" {
  name       = "${var.name_prefix}-db"
  subnet_ids = var.data_subnet_ids

  tags = {
    Name    = "${var.name_prefix}-db-subnets"
    Service = "platform"
  }
}

resource "aws_db_parameter_group" "postgres" {
  name_prefix = "${var.name_prefix}-postgres16-"
  family      = "postgres16"
  description = "${var.name_prefix} PostgreSQL 16 parameters"

  parameter {
    name  = "log_connections"
    value = "1"
  }

  parameter {
    name  = "log_disconnections"
    value = "1"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_db_instance" "postgres" {
  identifier = "${var.name_prefix}-postgres"

  engine         = "postgres"
  engine_version = "16"
  instance_class = var.rds_instance_class

  allocated_storage     = var.rds_allocated_storage
  max_allocated_storage = var.rds_max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true
  kms_key_id            = var.data_kms_key_arn

  db_name  = "meetingmind"
  username = "meetingmind_admin"
  port     = 5432

  manage_master_user_password   = true
  master_user_secret_kms_key_id = var.data_kms_key_arn

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [var.database_security_group_id]
  parameter_group_name   = aws_db_parameter_group.postgres.name

  multi_az            = false
  publicly_accessible = false

  backup_retention_period = 7
  backup_window           = "17:00-18:00"
  maintenance_window      = "sun:18:00-sun:19:00"

  auto_minor_version_upgrade = true
  apply_immediately          = false
  copy_tags_to_snapshot      = true
  deletion_protection        = true
  skip_final_snapshot        = false
  final_snapshot_identifier  = "${var.name_prefix}-postgres-final"

  performance_insights_enabled          = true
  performance_insights_kms_key_id       = var.data_kms_key_arn
  performance_insights_retention_period = 7

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]

  tags = {
    Name    = "${var.name_prefix}-postgres"
    Service = "platform"
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_elasticache_subnet_group" "this" {
  name       = "${var.name_prefix}-valkey"
  subnet_ids = var.data_subnet_ids

  tags = {
    Name    = "${var.name_prefix}-valkey-subnets"
    Service = "platform"
  }
}

resource "aws_elasticache_user" "bff" {
  user_id   = "${var.name_prefix}-bff"
  user_name = "${var.name_prefix}-bff"
  # Spring Boot's Redis readiness indicator uses INFO. Re-add only that
  # read-only command after excluding the broader dangerous command category.
  access_string = "on ~meetingmind:bff:* +@all -@dangerous +info"
  engine        = "valkey"

  authentication_mode {
    type = "iam"
  }

  tags = {
    Service = "bff"
  }
}

resource "aws_elasticache_user_group" "this" {
  engine        = "valkey"
  user_group_id = "${var.name_prefix}-valkey"
  user_ids = [
    aws_elasticache_user.bff.user_id,
  ]

  tags = {
    Service = "platform"
  }
}

resource "aws_elasticache_replication_group" "this" {
  replication_group_id = "${var.name_prefix}-valkey"
  description          = "MeetingMind BFF session and encrypted token vault"

  engine         = "valkey"
  engine_version = "7.2"
  node_type      = var.valkey_node_type
  port           = 6379

  num_cache_clusters         = 2
  automatic_failover_enabled = true
  multi_az_enabled           = true

  subnet_group_name  = aws_elasticache_subnet_group.this.name
  security_group_ids = [var.cache_security_group_id]
  user_group_ids     = [aws_elasticache_user_group.this.id]

  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  kms_key_id                 = var.data_kms_key_arn

  snapshot_retention_limit   = 1
  snapshot_window            = "16:00-17:00"
  maintenance_window         = "sun:19:00-sun:20:00"
  apply_immediately          = false
  auto_minor_version_upgrade = true

  tags = {
    Name    = "${var.name_prefix}-valkey"
    Service = "platform"
  }

  lifecycle {
    prevent_destroy = true
  }
}
