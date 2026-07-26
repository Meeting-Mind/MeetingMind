terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

data "aws_ec2_managed_prefix_list" "cloudfront_origin_facing" {
  count = var.enable_cloudfront_origin_ingress ? 1 : 0
  name  = "com.amazonaws.global.cloudfront.origin-facing"
}

data "aws_ec2_managed_prefix_list" "s3" {
  name = "com.amazonaws.${var.aws_region}.s3"
}

locals {
  service_ports = {
    bff          = 8081
    auth         = 8082
    core         = 8080
    ai           = 8000
    realtime-stt = 8083
  }

  internal_egress = {
    bff_to_auth = {
      source      = "bff"
      destination = "auth"
      port        = 8082
    }
    bff_to_core = {
      source      = "bff"
      destination = "core"
      port        = 8080
    }
    core_to_auth = {
      source      = "core"
      destination = "auth"
      port        = 8082
    }
    core_to_ai = {
      source      = "core"
      destination = "ai"
      port        = 8000
    }
    core_to_stt = {
      source      = "core"
      destination = "realtime-stt"
      port        = 8083
    }
  }

  database_clients = toset(["auth", "core", "ai", "realtime-stt"])
  internet_clients = toset(["auth", "core", "ai", "realtime-stt"])
}

resource "aws_security_group" "alb" {
  name_prefix            = "${var.name_prefix}-alb-"
  description            = "MeetingMind public ALB"
  vpc_id                 = var.vpc_id
  revoke_rules_on_delete = true

  tags = {
    Name    = "${var.name_prefix}-alb-sg"
    Service = "platform"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group" "service" {
  for_each = local.service_ports

  name_prefix            = "${var.name_prefix}-${each.key}-"
  description            = "MeetingMind ${each.key} ECS tasks"
  vpc_id                 = var.vpc_id
  revoke_rules_on_delete = true

  tags = {
    Name    = "${var.name_prefix}-${each.key}-sg"
    Service = each.key
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group" "database" {
  name_prefix            = "${var.name_prefix}-database-"
  description            = "MeetingMind RDS PostgreSQL"
  vpc_id                 = var.vpc_id
  revoke_rules_on_delete = true

  tags = {
    Name    = "${var.name_prefix}-database-sg"
    Service = "platform"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group" "cache" {
  name_prefix            = "${var.name_prefix}-cache-"
  description            = "MeetingMind ElastiCache for Valkey"
  vpc_id                 = var.vpc_id
  revoke_rules_on_delete = true

  tags = {
    Name    = "${var.name_prefix}-cache-sg"
    Service = "platform"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group" "endpoints" {
  name_prefix            = "${var.name_prefix}-endpoints-"
  description            = "MeetingMind VPC interface endpoints"
  vpc_id                 = var.vpc_id
  revoke_rules_on_delete = true

  tags = {
    Name    = "${var.name_prefix}-endpoints-sg"
    Service = "platform"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  for_each = var.enable_http_smoke_listener ? toset(var.allowed_ingress_cidrs) : toset([])

  security_group_id = aws_security_group.alb.id
  description       = "Temporary HTTP smoke access"
  cidr_ipv4         = each.value
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "alb_http_from_cloudfront" {
  count = var.enable_cloudfront_origin_ingress ? 1 : 0

  security_group_id = aws_security_group.alb.id
  description       = "CloudFront origin-facing HTTP to ALB"
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront_origin_facing[0].id
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "alb_https" {
  for_each = var.enable_https_listener ? toset(var.allowed_ingress_cidrs) : toset([])

  security_group_id = aws_security_group.alb.id
  description       = "HTTPS access before CloudFront-only restriction"
  cidr_ipv4         = each.value
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "bff_from_alb" {
  security_group_id            = aws_security_group.service["bff"].id
  description                  = "ALB to BFF"
  referenced_security_group_id = aws_security_group.alb.id
  from_port                    = local.service_ports.bff
  to_port                      = local.service_ports.bff
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "stt_from_alb" {
  security_group_id            = aws_security_group.service["realtime-stt"].id
  description                  = "ALB to Realtime STT WSS"
  referenced_security_group_id = aws_security_group.alb.id
  from_port                    = local.service_ports.realtime-stt
  to_port                      = local.service_ports.realtime-stt
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "auth_from_bff" {
  security_group_id            = aws_security_group.service["auth"].id
  description                  = "BFF to Auth"
  referenced_security_group_id = aws_security_group.service["bff"].id
  from_port                    = local.service_ports.auth
  to_port                      = local.service_ports.auth
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "auth_from_core" {
  security_group_id            = aws_security_group.service["auth"].id
  description                  = "Core to Auth JWKS"
  referenced_security_group_id = aws_security_group.service["core"].id
  from_port                    = local.service_ports.auth
  to_port                      = local.service_ports.auth
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "core_from_bff" {
  security_group_id            = aws_security_group.service["core"].id
  description                  = "BFF to Core"
  referenced_security_group_id = aws_security_group.service["bff"].id
  from_port                    = local.service_ports.core
  to_port                      = local.service_ports.core
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "ai_from_core" {
  security_group_id            = aws_security_group.service["ai"].id
  description                  = "Core to AI"
  referenced_security_group_id = aws_security_group.service["core"].id
  from_port                    = local.service_ports.ai
  to_port                      = local.service_ports.ai
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "stt_from_core" {
  security_group_id            = aws_security_group.service["realtime-stt"].id
  description                  = "Core to Realtime STT"
  referenced_security_group_id = aws_security_group.service["core"].id
  from_port                    = local.service_ports.realtime-stt
  to_port                      = local.service_ports.realtime-stt
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "database" {
  for_each = toset(["auth", "core", "ai", "realtime-stt"])

  security_group_id            = aws_security_group.database.id
  description                  = "${each.value} to PostgreSQL"
  referenced_security_group_id = aws_security_group.service[each.value].id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "cache" {
  security_group_id            = aws_security_group.cache.id
  description                  = "BFF to Valkey"
  referenced_security_group_id = aws_security_group.service["bff"].id
  from_port                    = 6379
  to_port                      = 6379
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "endpoints" {
  for_each = aws_security_group.service

  security_group_id            = aws_security_group.endpoints.id
  description                  = "${each.key} to VPC endpoints"
  referenced_security_group_id = each.value.id
  from_port                    = 443
  to_port                      = 443
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_bff" {
  security_group_id            = aws_security_group.alb.id
  description                  = "ALB to BFF"
  referenced_security_group_id = aws_security_group.service["bff"].id
  from_port                    = local.service_ports.bff
  to_port                      = local.service_ports.bff
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_stt" {
  security_group_id            = aws_security_group.alb.id
  description                  = "ALB to Realtime STT"
  referenced_security_group_id = aws_security_group.service["realtime-stt"].id
  from_port                    = local.service_ports.realtime-stt
  to_port                      = local.service_ports.realtime-stt
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "internal" {
  for_each = local.internal_egress

  security_group_id            = aws_security_group.service[each.value.source].id
  description                  = replace(each.key, "_", " ")
  referenced_security_group_id = aws_security_group.service[each.value.destination].id
  from_port                    = each.value.port
  to_port                      = each.value.port
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "database" {
  for_each = local.database_clients

  security_group_id            = aws_security_group.service[each.key].id
  description                  = "${each.key} to PostgreSQL"
  referenced_security_group_id = aws_security_group.database.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "cache" {
  security_group_id            = aws_security_group.service["bff"].id
  description                  = "BFF to Valkey"
  referenced_security_group_id = aws_security_group.cache.id
  from_port                    = 6379
  to_port                      = 6379
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "bff_to_s3" {
  security_group_id = aws_security_group.service["bff"].id
  description       = "BFF ECR image layers through the regional S3 gateway endpoint"
  prefix_list_id    = data.aws_ec2_managed_prefix_list.s3.id
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "endpoints" {
  for_each = aws_security_group.service

  security_group_id            = each.value.id
  description                  = "${each.key} to VPC endpoints"
  referenced_security_group_id = aws_security_group.endpoints.id
  from_port                    = 443
  to_port                      = 443
  ip_protocol                  = "tcp"
}

# These services call dynamic-IP Google/OpenAI/LiveKit/STT providers through the Regional NAT.
# Replace this reviewed TCP/443 exception with an approved domain-filtering egress control before Production.
#trivy:ignore:AVD-AWS-0104:exp:2026-10-31
resource "aws_vpc_security_group_egress_rule" "internet_https" {
  for_each = local.internet_clients

  security_group_id = aws_security_group.service[each.key].id
  description       = "${each.key} external provider HTTPS through Regional NAT"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}
