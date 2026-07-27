variable "name_prefix" {
  description = "Lowercase name prefix used for the frontend bucket and CloudFront resources."
  type        = string

  validation {
    condition     = length(var.name_prefix) <= 40 && can(regex("^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$", var.name_prefix))
    error_message = "name_prefix must be at most 40 lowercase alphanumeric or hyphen characters and cannot start or end with a hyphen."
  }
}

variable "alb_dns_name" {
  description = "DNS name of the existing public ALB used as the /api/* CloudFront origin."
  type        = string

  validation {
    condition     = can(regex("^[A-Za-z0-9.-]+$", var.alb_dns_name)) && !strcontains(var.alb_dns_name, "://")
    error_message = "alb_dns_name must be a DNS name without a URL scheme or path."
  }
}

variable "alb_origin_id" {
  description = "Stable CloudFront origin identifier for the existing BFF ALB."
  type        = string

  validation {
    condition     = length(trimspace(var.alb_origin_id)) > 0 && length(var.alb_origin_id) <= 128
    error_message = "alb_origin_id must be between 1 and 128 characters."
  }
}

variable "custom_domain" {
  description = "Optional custom viewer domain and existing us-east-1 ACM certificate used by CloudFront."
  type = object({
    name                = string
    acm_certificate_arn = string
  })
  default = null

  validation {
    condition = var.custom_domain == null || (
      try(var.custom_domain.name, "") == lower(try(var.custom_domain.name, "")) &&
      can(regex("^[a-z0-9][a-z0-9.-]*[a-z0-9]$", try(var.custom_domain.name, ""))) &&
      strcontains(try(var.custom_domain.name, ""), ".") &&
      !strcontains(try(var.custom_domain.name, ""), "..") &&
      can(regex("^arn:aws:acm:us-east-1:[0-9]{12}:certificate/[0-9a-f-]{36}$", try(var.custom_domain.acm_certificate_arn, "")))
    )
    error_message = "custom_domain must contain a lowercase DNS name and a us-east-1 ACM certificate ARN."
  }
}

variable "tags" {
  description = "Tags applied to taggable frontend edge resources."
  type        = map(string)
  default     = {}
}
