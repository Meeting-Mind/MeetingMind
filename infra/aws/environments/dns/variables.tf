variable "aws_region" {
  description = "AWS region for the provider. Route 53 itself is global."
  type        = string
  default     = "ap-northeast-2"

  validation {
    condition     = var.aws_region == "ap-northeast-2"
    error_message = "MeetingMind is fixed to ap-northeast-2."
  }
}

variable "expected_aws_account_id" {
  description = "AWS account ID allowed by the provider."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.expected_aws_account_id))
    error_message = "expected_aws_account_id must be a 12-digit AWS account ID."
  }
}

variable "root_domain" {
  description = "Registrable root domain. Registration stays with the external registrar (Gabia); this root owns DNS hosting only."
  type        = string
  default     = "meetingmind.co.kr"
}

variable "app_subdomain" {
  description = "Subdomain served by the NonProd CloudFront distribution."
  type        = string
  default     = "app"
}

variable "cloudfront_distribution_id" {
  description = "CloudFront distribution that serves app_subdomain. Owned by the nonprod-v2 root and read here as a data source."
  type        = string

  validation {
    condition     = can(regex("^[A-Z0-9]+$", var.cloudfront_distribution_id))
    error_message = "cloudfront_distribution_id must be a CloudFront distribution ID."
  }
}

