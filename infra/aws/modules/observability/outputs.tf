output "log_group_names" {
  value = { for name, group in aws_cloudwatch_log_group.service : name => group.name }
}

output "alarm_topic_arn" {
  value = aws_sns_topic.alarms.arn
}

output "dashboard_name" {
  value = aws_cloudwatch_dashboard.this.dashboard_name
}
