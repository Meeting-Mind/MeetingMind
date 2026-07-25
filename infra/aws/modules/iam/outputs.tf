output "execution_role_arns" {
  value = { for name, role in aws_iam_role.execution : name => role.arn }
}

output "task_role_arns" {
  value = { for name, role in aws_iam_role.task : name => role.arn }
}

output "github_deploy_role_arn" {
  value = try(aws_iam_role.github_deploy[0].arn, null)
}
