output "hosted_zone_id" {
  description = "Public hosted zone ID for the root domain."
  value       = aws_route53_zone.root.zone_id
}

output "registrar_nameservers" {
  description = "Nameservers to enter at the registrar (Gabia) during stage 2 of the migration."
  value       = aws_route53_zone.root.name_servers
}

output "app_fqdn" {
  description = "Hostname aliased to the NonProd CloudFront distribution."
  value       = local.app_fqdn
}

# Feed this into environments/nonprod-v2 as frontend_custom_domain.acm_certificate_arn. It is the
# validation resource's ARN rather than the certificate's so that consumers cannot read it before
# ACM has finished issuing.
output "app_certificate_arn" {
  description = "us-east-1 ACM certificate ARN for the CloudFront viewer certificate."
  value       = aws_acm_certificate_validation.app.certificate_arn
}
