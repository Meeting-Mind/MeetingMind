output "arn" {
  value = aws_lb.this.arn
}

output "arn_suffix" {
  value = aws_lb.this.arn_suffix
}

output "dns_name" {
  value = aws_lb.this.dns_name
}

output "zone_id" {
  value = aws_lb.this.zone_id
}

output "target_group_arns" {
  value = {
    bff          = aws_lb_target_group.bff.arn
    realtime-stt = aws_lb_target_group.stt.arn
  }
}

output "target_group_arn_suffixes" {
  value = {
    bff          = aws_lb_target_group.bff.arn_suffix
    realtime-stt = aws_lb_target_group.stt.arn_suffix
  }
}
