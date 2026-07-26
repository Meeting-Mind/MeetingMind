output "bucket_id" {
  description = "Private S3 bucket name for frontend build uploads."
  value       = aws_s3_bucket.frontend.id
}

output "bucket_arn" {
  description = "ARN of the private frontend asset bucket."
  value       = aws_s3_bucket.frontend.arn
}

output "distribution_id" {
  description = "CloudFront distribution ID for cache invalidation and smoke checks."
  value       = aws_cloudfront_distribution.frontend.id
}

output "distribution_arn" {
  description = "ARN of the frontend CloudFront distribution."
  value       = aws_cloudfront_distribution.frontend.arn
}

output "distribution_domain_name" {
  description = "CloudFront default HTTPS domain used for NonProd smoke testing."
  value       = aws_cloudfront_distribution.frontend.domain_name
}

output "static_origin_id" {
  description = "CloudFront origin ID of the private S3 static origin."
  value       = local.static_origin_id
}

output "alb_origin_id" {
  description = "CloudFront origin ID used for /api/* requests."
  value       = var.alb_origin_id
}
