variable "name_prefix" {
  type = string
}

variable "kms_key_arn" {
  type = string
}

variable "secrets" {
  description = "Secret name suffix to description. This module intentionally creates no secret versions."
  type        = map(string)
}

variable "tls_bundle_read_principals" {
  description = "TLS bundle secret key to the exact task-role ARN allowed to read it. Every other principal is explicitly denied GetSecretValue."
  type        = map(string)
  default     = {}

  validation {
    condition     = length(setsubtract(toset(keys(var.tls_bundle_read_principals)), toset(keys(var.secrets)))) == 0
    error_message = "tls_bundle_read_principals contains a secret key that is not declared in secrets."
  }
}
