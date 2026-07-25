variable "name_prefix" {
  type = string
}

variable "services" {
  type = set(string)
}

variable "service_secret_arns" {
  type = map(set(string))

  validation {
    condition     = length(setsubtract(toset(keys(var.service_secret_arns)), var.services)) == 0
    error_message = "service_secret_arns contains a service that is not declared in services."
  }
}

variable "service_tls_secret_arns" {
  description = "Service to its exact TLS bundle secret ARN. Grants only the cert-loader task-role read path."
  type        = map(string)
  default     = {}

  validation {
    condition     = length(setsubtract(toset(keys(var.service_tls_secret_arns)), var.services)) == 0
    error_message = "service_tls_secret_arns contains a service that is not declared in services."
  }
}

variable "tls_kms_key_arn" {
  description = "Exact KMS key ARN that encrypts the TLS bundle secrets."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = length(var.service_tls_secret_arns) == 0 || var.tls_kms_key_arn != null
    error_message = "tls_kms_key_arn is required when service_tls_secret_arns is set."
  }
}

variable "decrypt_kms_key_arns" {
  type = list(string)
}

variable "application_kms_key_arn" {
  type = string
}

variable "jwt_signing_kms_key_arn" {
  type = string
}

variable "valkey_replication_group_arn" {
  type = string
}

variable "valkey_user_arn" {
  type = string
}

variable "ecr_repository_arns" {
  type = list(string)
}

variable "enable_github_oidc" {
  type    = bool
  default = false
}

variable "github_oidc_provider_arn" {
  type     = string
  default  = null
  nullable = true
}

variable "github_repository" {
  type    = string
  default = "Meeting-Mind/MeetingMind"
}

variable "github_oidc_subjects" {
  description = "Exact protected GitHub OIDC subject patterns allowed to deploy."
  type        = list(string)
  default     = []

  validation {
    condition     = alltrue([for subject in var.github_oidc_subjects : startswith(subject, "repo:${var.github_repository}:") && !endswith(subject, ":*")])
    error_message = "GitHub subjects must belong to github_repository and cannot grant a repository-wide wildcard."
  }
}
