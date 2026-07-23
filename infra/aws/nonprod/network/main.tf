terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = local.common_tags
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  selected_azs = slice(data.aws_availability_zones.available.names, 0, var.az_count)

  common_tags = {
    Project     = "MeetingMind"
    Environment = "nonprod"
    ManagedBy   = "terraform"
    Stage       = "network-baseline"
  }

  public_subnets = {
    for index, cidr in var.public_subnet_cidrs : "public-${index + 1}" => {
      cidr = cidr
      az   = local.selected_azs[index]
    }
  }

  private_subnets = {
    for index, cidr in var.private_subnet_cidrs : "private-${index + 1}" => {
      cidr = cidr
      az   = local.selected_azs[index]
    }
  }

  data_subnets = {
    for index, cidr in var.data_subnet_cidrs : "data-${index + 1}" => {
      cidr = cidr
      az   = local.selected_azs[index]
    }
  }
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "meetingmind-nonprod-vpc"
  }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = {
    Name = "meetingmind-nonprod-igw"
  }
}

resource "aws_subnet" "public" {
  for_each = local.public_subnets

  vpc_id                  = aws_vpc.this.id
  cidr_block              = each.value.cidr
  availability_zone       = each.value.az
  map_public_ip_on_launch = true

  tags = {
    Name = "meetingmind-nonprod-${each.key}"
    Tier = "public"
  }
}

resource "aws_subnet" "private" {
  for_each = local.private_subnets

  vpc_id            = aws_vpc.this.id
  cidr_block        = each.value.cidr
  availability_zone = each.value.az

  tags = {
    Name = "meetingmind-nonprod-${each.key}"
    Tier = "private"
  }
}

resource "aws_subnet" "data" {
  for_each = local.data_subnets

  vpc_id            = aws_vpc.this.id
  cidr_block        = each.value.cidr
  availability_zone = each.value.az

  tags = {
    Name = "meetingmind-nonprod-${each.key}"
    Tier = "data"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = {
    Name = "meetingmind-nonprod-public-rt"
    Tier = "public"
  }
}

resource "aws_route_table_association" "public" {
  for_each = aws_subnet.public

  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private" {
  for_each = aws_subnet.private

  vpc_id = aws_vpc.this.id

  tags = {
    Name = "meetingmind-nonprod-${each.key}-rt"
    Tier = "private"
  }
}

resource "aws_route_table_association" "private" {
  for_each = aws_subnet.private

  subnet_id      = each.value.id
  route_table_id = aws_route_table.private[each.key].id
}

resource "aws_route_table" "data" {
  for_each = aws_subnet.data

  vpc_id = aws_vpc.this.id

  tags = {
    Name = "meetingmind-nonprod-${each.key}-rt"
    Tier = "data"
  }
}

resource "aws_route_table_association" "data" {
  for_each = aws_subnet.data

  subnet_id      = each.value.id
  route_table_id = aws_route_table.data[each.key].id
}
