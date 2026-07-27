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
