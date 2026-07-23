variable "aws_region" {
  description = "AWS region for the NonProd network."
  type        = string
  default     = "ap-northeast-2"
}

variable "az_count" {
  description = "Number of availability zones used by the initial NonProd VPC."
  type        = number
  default     = 2

  validation {
    condition     = var.az_count == 2
    error_message = "MeetingMind NonProd starts with exactly two AZs."
  }
}

variable "vpc_cidr" {
  description = "CIDR block for the MeetingMind NonProd VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "Public subnet CIDRs, one per AZ."
  type        = list(string)
  default     = ["10.20.0.0/24", "10.20.1.0/24"]

  validation {
    condition     = length(var.public_subnet_cidrs) == 2
    error_message = "Provide exactly two public subnet CIDRs for the initial NonProd VPC."
  }
}

variable "private_subnet_cidrs" {
  description = "Private application subnet CIDRs, one per AZ."
  type        = list(string)
  default     = ["10.20.16.0/20", "10.20.32.0/20"]

  validation {
    condition     = length(var.private_subnet_cidrs) == 2
    error_message = "Provide exactly two private subnet CIDRs for the initial NonProd VPC."
  }
}

variable "data_subnet_cidrs" {
  description = "Data subnet CIDRs, one per AZ."
  type        = list(string)
  default     = ["10.20.48.0/24", "10.20.49.0/24"]

  validation {
    condition     = length(var.data_subnet_cidrs) == 2
    error_message = "Provide exactly two data subnet CIDRs for the initial NonProd VPC."
  }
}
