output "bucket_name" {
  description = "S3 bucket used by application Terraform states."
  value       = aws_s3_bucket.state.id
}

output "kms_key_arn" {
  description = "KMS key used to encrypt Terraform state."
  value       = aws_kms_key.state.arn
}

output "backend_hcl" {
  description = "Non-secret backend configuration values."
  value = {
    bucket       = aws_s3_bucket.state.id
    key          = "nonprod-v2/terraform.tfstate"
    region       = var.aws_region
    encrypt      = true
    kms_key_id   = aws_kms_key.state.arn
    use_lockfile = true
  }
}
