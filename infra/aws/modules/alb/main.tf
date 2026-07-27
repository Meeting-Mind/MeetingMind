terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

# Internet-facing is the approved D-023 edge shape; ingress remains empty until a restricted listener is enabled.
#trivy:ignore:AVD-AWS-0053:exp:2026-10-31
resource "aws_lb" "this" {
  name               = substr("${var.name_prefix}-alb", 0, 32)
  internal           = false
  load_balancer_type = "application"
  security_groups    = [var.security_group_id]
  subnets            = var.public_subnet_ids

  enable_deletion_protection = true
  drop_invalid_header_fields = true
  idle_timeout               = 60

  tags = {
    Name    = "${var.name_prefix}-alb"
    Service = "platform"
  }
}

resource "aws_lb_target_group" "bff" {
  name        = substr("${var.name_prefix}-bff", 0, 32)
  port        = 8081
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = var.vpc_id

  deregistration_delay = 30

  health_check {
    enabled             = true
    healthy_threshold   = 2
    interval            = 30
    matcher             = "200"
    path                = "/actuator/health/readiness"
    port                = "traffic-port"
    protocol            = "HTTP"
    timeout             = 5
    unhealthy_threshold = 3
  }

  tags = {
    Service = "bff"
  }
}

resource "aws_lb_target_group" "stt" {
  name        = substr("${var.name_prefix}-stt", 0, 32)
  port        = 8083
  protocol    = "HTTPS"
  target_type = "ip"
  vpc_id      = var.vpc_id

  deregistration_delay = 30

  health_check {
    enabled             = true
    healthy_threshold   = 2
    interval            = 30
    matcher             = "200"
    path                = "/actuator/health/readiness"
    port                = "9083"
    protocol            = "HTTP"
    timeout             = 5
    unhealthy_threshold = 3
  }

  tags = {
    Service = "realtime-stt"
  }
}

resource "aws_lb_listener" "http_smoke" {
  count = var.enable_http_smoke_listener ? 1 : 0

  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "application/json"
      message_body = "{\"code\":\"ROUTE_NOT_ALLOWED\"}"
      status_code  = "404"
    }
  }
}

resource "aws_lb_listener_rule" "bff" {
  count = var.enable_http_smoke_listener ? 1 : 0

  listener_arn = aws_lb_listener.http_smoke[0].arn
  priority     = 100

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.bff.arn
  }

  condition {
    path_pattern {
      values = ["/api/*", "/actuator/health/*"]
    }
  }
}

resource "aws_lb_listener_rule" "stt" {
  count = var.enable_http_smoke_listener && var.enable_stt_smoke_route ? 1 : 0

  listener_arn = aws_lb_listener.http_smoke[0].arn
  priority     = 90

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.stt.arn
  }

  condition {
    path_pattern {
      values = ["/ws/egress-audio/*"]
    }
  }
}
