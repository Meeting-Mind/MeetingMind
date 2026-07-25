output "rds_identifier" {
  value = aws_db_instance.postgres.identifier
}

output "rds_address" {
  value = aws_db_instance.postgres.address
}

output "rds_port" {
  value = aws_db_instance.postgres.port
}

output "rds_master_secret_arn" {
  value     = try(aws_db_instance.postgres.master_user_secret[0].secret_arn, null)
  sensitive = true
}

output "valkey_replication_group_id" {
  value = aws_elasticache_replication_group.this.replication_group_id
}

output "valkey_replication_group_arn" {
  value = aws_elasticache_replication_group.this.arn
}

output "valkey_primary_endpoint" {
  value = aws_elasticache_replication_group.this.primary_endpoint_address
}

output "valkey_port" {
  value = aws_elasticache_replication_group.this.port
}

output "valkey_bff_user_arn" {
  value = aws_elasticache_user.bff.arn
}

output "valkey_bff_username" {
  value = aws_elasticache_user.bff.user_name
}
