output "application_key_arn" {
  value = aws_kms_key.application.arn
}

output "data_key_arn" {
  value = aws_kms_key.data.arn
}

output "logs_key_arn" {
  value = aws_kms_key.logs.arn
}

output "jwt_signing_key_arn" {
  value = aws_kms_key.jwt_signing.arn
}

output "all_key_arns" {
  value = [
    aws_kms_key.application.arn,
    aws_kms_key.data.arn,
    aws_kms_key.logs.arn,
    aws_kms_key.jwt_signing.arn,
  ]
}
