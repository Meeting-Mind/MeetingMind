variable "name_prefix" {
  type = string
}

variable "vpc_cidr" {
  type = string
}

variable "availability_zones" {
  type = list(string)

  validation {
    condition     = length(var.availability_zones) == 2
    error_message = "MeetingMind NonProd V2 requires exactly two availability zones."
  }
}

variable "public_subnet_cidrs" {
  type = list(string)

  validation {
    condition     = length(var.public_subnet_cidrs) == 2
    error_message = "Provide exactly two public subnet CIDRs."
  }
}

variable "private_subnet_cidrs" {
  type = list(string)

  validation {
    condition     = length(var.private_subnet_cidrs) == 2
    error_message = "Provide exactly two private application subnet CIDRs."
  }
}

variable "data_subnet_cidrs" {
  type = list(string)

  validation {
    condition     = length(var.data_subnet_cidrs) == 2
    error_message = "Provide exactly two data subnet CIDRs."
  }
}

variable "logs_kms_key_arn" {
  type = string
}

variable "flow_log_retention_days" {
  type    = number
  default = 7
}
