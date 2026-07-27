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
  description = "CloudFront-generated HTTPS domain used as the external DNS CNAME target."
  value       = aws_cloudfront_distribution.frontend.domain_name
}

output "custom_domain_configuration" {
  description = "Effective custom viewer-domain configuration, or null when the default CloudFront certificate is used."
  value = var.custom_domain == null ? null : {
    name                     = one(aws_cloudfront_distribution.frontend.aliases)
    acm_certificate_arn      = one(aws_cloudfront_distribution.frontend.viewer_certificate).acm_certificate_arn
    minimum_protocol_version = one(aws_cloudfront_distribution.frontend.viewer_certificate).minimum_protocol_version
    ssl_support_method       = one(aws_cloudfront_distribution.frontend.viewer_certificate).ssl_support_method
  }
}

output "static_origin_id" {
  description = "CloudFront origin ID of the private S3 static origin."
  value       = local.static_origin_id
}

output "alb_origin_id" {
  description = "CloudFront origin ID used for /api/* requests."
  value       = var.alb_origin_id
}

output "stt_websocket_behavior" {
  description = "CloudFront behavior that carries token-protected LiveKit Egress WebSocket traffic."
  value = one([
    for behavior in aws_cloudfront_distribution.frontend.ordered_cache_behavior : {
      path_pattern             = behavior.path_pattern
      target_origin_id         = behavior.target_origin_id
      viewer_protocol_policy   = behavior.viewer_protocol_policy
      cache_policy_id          = behavior.cache_policy_id
      origin_request_policy_id = behavior.origin_request_policy_id
      compress                 = behavior.compress
    } if behavior.path_pattern == "/ws/egress-audio/*"
  ])
}
