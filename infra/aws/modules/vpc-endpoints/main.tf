terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

locals {
  interface_services = toset([
    "ecr.api",
    "ecr.dkr",
    "kms",
    "logs",
    "secretsmanager",
  ])
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = var.vpc_id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = concat(var.private_route_table_ids, var.data_route_table_ids)

  tags = {
    Name    = "${var.name_prefix}-s3-endpoint"
    Service = "platform"
  }
}

resource "aws_vpc_endpoint" "interface" {
  for_each = local.interface_services

  vpc_id              = var.vpc_id
  service_name        = "com.amazonaws.${var.aws_region}.${each.value}"
  vpc_endpoint_type   = "Interface"
  private_dns_enabled = true
  subnet_ids          = var.private_subnet_ids
  security_group_ids  = [var.security_group_id]

  tags = {
    Name    = "${var.name_prefix}-${replace(each.value, ".", "-")}-endpoint"
    Service = "platform"
  }
}
