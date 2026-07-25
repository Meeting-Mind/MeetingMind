output "alb_security_group_id" {
  value = aws_security_group.alb.id
}

output "service_security_group_ids" {
  value = { for name, group in aws_security_group.service : name => group.id }
}

output "database_security_group_id" {
  value = aws_security_group.database.id
}

output "cache_security_group_id" {
  value = aws_security_group.cache.id
}

output "endpoints_security_group_id" {
  value = aws_security_group.endpoints.id
}
