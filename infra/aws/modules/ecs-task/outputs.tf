output "arn" {
  value = aws_ecs_task_definition.this.arn
}

output "family" {
  value = aws_ecs_task_definition.this.family
}

output "container_names" {
  value = [for container in local.containers : container.name]
}
