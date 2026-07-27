# Stage 1 of the Gabia -> Route 53 DNS migration. This root creates the public hosted zone and
# mirrors every record that currently answers from Gabia. Nothing here changes live resolution:
# the domain keeps resolving through Gabia until the registrar nameservers are repointed by hand
# (stage 2). See README.md for the ordering constraints.

data "aws_cloudfront_distribution" "frontend" {
  id = var.cloudfront_distribution_id
}

# Deliberately NOT owned by environments/nonprod-v2. Destroying that environment must never
# delete the zone: a recreated zone gets new nameservers, which takes the domain down until the
# registrar is updated again.
resource "aws_route53_zone" "root" {
  name    = var.root_domain
  comment = "MeetingMind root domain. Registrar stays external; DNS hosting is managed here."

  lifecycle {
    prevent_destroy = true
  }
}

# ALIAS rather than CNAME. CNAME cannot be used at a zone apex, and CloudFront addresses are not
# static, so ALIAS is what lets the root domain point at the distribution later.
resource "aws_route53_record" "app_ipv4" {
  zone_id = aws_route53_zone.root.zone_id
  name    = local.app_fqdn
  type    = "A"

  alias {
    name                   = data.aws_cloudfront_distribution.frontend.domain_name
    zone_id                = data.aws_cloudfront_distribution.frontend.hosted_zone_id
    evaluate_target_health = false
  }
}

# The distribution enables IPv6, so viewers on IPv6-only networks need an AAAA alias too.
resource "aws_route53_record" "app_ipv6" {
  zone_id = aws_route53_zone.root.zone_id
  name    = local.app_fqdn
  type    = "AAAA"

  alias {
    name                   = data.aws_cloudfront_distribution.frontend.domain_name
    zone_id                = data.aws_cloudfront_distribution.frontend.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "legacy_acm_validation" {
  for_each = var.legacy_acm_validation_records

  zone_id = aws_route53_zone.root.zone_id
  name    = each.key
  type    = "CNAME"
  ttl     = 300
  records = [each.value]
}
