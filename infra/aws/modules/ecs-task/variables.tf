variable "family" {
  type = string
}

variable "container_name" {
  type = string
}

variable "image" {
  type = string
}

variable "container_port" {
  type = number
}

variable "cpu" {
  type = number
}

variable "memory" {
  type = number
}

variable "execution_role_arn" {
  type = string
}

variable "task_role_arn" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "log_group_name" {
  type = string
}

variable "environment" {
  type    = map(string)
  default = {}
}

variable "secrets" {
  type    = map(string)
  default = {}
}

variable "health_check_command" {
  type    = list(string)
  default = []
}

variable "container_command" {
  description = "Optional application container command override."
  type        = list(string)
  default     = []
}

variable "background_workers" {
  description = "Additional essential containers that reuse the application image, environment and secrets with a dedicated command."
  type        = map(list(string))
  default     = {}

  validation {
    condition     = alltrue([for command in values(var.background_workers) : length(command) > 0])
    error_message = "Every background worker must define a non-empty command."
  }
}

variable "envoy_sidecar" {
  description = "mTLS termination sidecar that takes over the task port. Requires tls_bundle for the shared TLS volume."
  type = object({
    image = string
  })
  default  = null
  nullable = true

  validation {
    condition     = var.envoy_sidecar == null || var.tls_bundle != null
    error_message = "envoy_sidecar requires tls_bundle so the shared TLS volume exists."
  }
}

variable "tls_bundle" {
  description = "cert-loader wiring for the shared task TLS volume. Null keeps the task definition without mTLS material delivery."
  type = object({
    loader_image       = string
    secret_arn         = string
    version_stage      = optional(string, "AWSCURRENT")
    expected_service   = string
    expected_spiffe_id = string
    expected_dns_sans  = list(string)
    expected_ekus      = list(string)
  })
  default  = null
  nullable = true
}

variable "ephemeral_storage_gib" {
  type    = number
  default = 21

  validation {
    condition     = var.ephemeral_storage_gib >= 21 && var.ephemeral_storage_gib <= 200
    error_message = "Fargate ephemeral storage must be between 21 and 200 GiB when explicitly configured."
  }
}
