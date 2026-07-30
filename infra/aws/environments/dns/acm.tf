# Stage 3 of the DNS migration: the CloudFront viewer certificate, issued and renewed by
# Terraform instead of by hand. The certificate lives here rather than in environments/nonprod-v2
# because DNS validation needs the hosted zone, and keeping both in one root lets Terraform run
# issuance to completion without a manual step. nonprod-v2 consumes only the ARN.

resource "aws_acm_certificate" "app" {
  provider = aws.us_east_1

  domain_name       = local.app_fqdn
  validation_method = "DNS"

  # The apex is intentionally excluded: the BFF session cookie is __Host- prefixed and cannot be
  # shared across hostnames, so the service is served from a single host. See README.md.

  lifecycle {
    create_before_destroy = true
  }
}

# ACM derives this record name from the domain, so a reissue for the same domain reuses it. The
# externally issued certificate's record is still tracked as legacy_acm_validation_records until
# that certificate is retired, hence allow_overwrite.
resource "aws_route53_record" "acm_validation" {
  for_each = {
    for option in aws_acm_certificate.app.domain_validation_options :
    option.domain_name => {
      name  = option.resource_record_name
      type  = option.resource_record_type
      value = option.resource_record_value
    }
  }

  zone_id         = aws_route53_zone.root.zone_id
  name            = each.value.name
  type            = each.value.type
  ttl             = 300
  records         = [each.value.value]
  allow_overwrite = true
}

# Blocks until ACM reports the certificate as issued, so downstream consumers never receive the
# ARN of a certificate that CloudFront would reject.
resource "aws_acm_certificate_validation" "app" {
  provider = aws.us_east_1

  certificate_arn         = aws_acm_certificate.app.arn
  validation_record_fqdns = [for record in aws_route53_record.acm_validation : record.fqdn]
}
