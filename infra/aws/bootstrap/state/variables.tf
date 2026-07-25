variable "aws_region" {
  description = "AWS region for the Terraform state resources."
  type        = string
  default     = "ap-northeast-2"
}

variable "expected_aws_account_id" {
  description = "AWS account ID that is allowed to receive the state resources."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.expected_aws_account_id))
    error_message = "expected_aws_account_id must be a 12-digit AWS account ID."
  }
}

variable "bucket_name" {
  description = "Globally unique S3 bucket name. Keep the account ID in the name."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$", var.bucket_name))
    error_message = "bucket_name must be a valid S3 bucket name."
  }
}
