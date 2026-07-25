output "arn" {
  value = try(aws_ecs_service.this[0].id, null)
}

output "name" {
  value = var.name
}
