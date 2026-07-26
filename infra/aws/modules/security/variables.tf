variable "name_prefix" {
  type = string
}

variable "aws_region" {
  description = "AWS region used to resolve regional managed service prefix lists."
  type        = string
}

variable "vpc_id" {
  type = string
}

variable "allowed_ingress_cidrs" {
  description = "Operator CIDRs allowed during the temporary ALB smoke phase."
  type        = list(string)
  default     = []

  validation {
    condition     = alltrue([for cidr in var.allowed_ingress_cidrs : can(cidrhost(cidr, 0)) && cidr != "0.0.0.0/0"])
    error_message = "allowed_ingress_cidrs must contain valid restricted CIDRs; 0.0.0.0/0 is forbidden."
  }
}

variable "enable_http_smoke_listener" {
  type    = bool
  default = false
}

variable "enable_cloudfront_origin_ingress" {
  description = "Allows HTTP only from the AWS-managed CloudFront origin-facing prefix list."
  type        = bool
  default     = false
}

variable "enable_https_listener" {
  type    = bool
  default = false
}
