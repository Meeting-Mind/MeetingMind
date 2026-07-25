terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

resource "aws_service_discovery_private_dns_namespace" "this" {
  name        = var.namespace_name
  description = "MeetingMind private ECS service discovery"
  vpc         = var.vpc_id

  tags = {
    Name    = "${var.name_prefix}-service-discovery"
    Service = "platform"
  }
}

resource "aws_service_discovery_service" "this" {
  for_each = var.services

  name = each.value

  dns_config {
    namespace_id   = aws_service_discovery_private_dns_namespace.this.id
    routing_policy = "MULTIVALUE"

    dns_records {
      ttl  = var.dns_ttl_seconds
      type = "A"
    }
  }

  health_check_custom_config {
    failure_threshold = 1
  }

  tags = {
    Name    = "${each.value}.${var.namespace_name}"
    Service = each.key
  }
}
