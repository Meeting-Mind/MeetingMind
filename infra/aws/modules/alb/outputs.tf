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

output "stt_target_configuration" {
  value = {
    protocol              = aws_lb_target_group.stt.protocol
    port                  = aws_lb_target_group.stt.port
    health_check_protocol = one(aws_lb_target_group.stt.health_check).protocol
    health_check_port     = one(aws_lb_target_group.stt.health_check).port
    health_check_path     = one(aws_lb_target_group.stt.health_check).path
  }
}
