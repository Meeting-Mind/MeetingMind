terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}

locals {
  bucket_name      = "${var.name_prefix}-frontend-${data.aws_caller_identity.current.account_id}"
  static_origin_id = "${var.name_prefix}-frontend-s3"
  resource_tags = merge(var.tags, {
    Component = "frontend-edge"
  })
}

resource "aws_s3_bucket" "frontend" {
  bucket = local.bucket_name

  tags = merge(local.resource_tags, {
    Name = local.bucket_name
  })
}

resource "aws_s3_bucket_ownership_controls" "frontend" {
  bucket = aws_s3_bucket.frontend.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket = aws_s3_bucket.frontend.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Static build artifacts contain no user data or secrets. The bounded smoke uses SSE-S3 so
# CloudFront OAC can read without broadening the existing application KMS key policy.
# Replace with a dedicated frontend CMK before the formal T059 edge release.
#trivy:ignore:AVD-AWS-0132:exp:2026-08-09
resource "aws_s3_bucket_server_side_encryption_configuration" "frontend" {
  bucket = aws_s3_bucket.frontend.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = substr("${var.name_prefix}-frontend-oac", 0, 64)
  description                       = "OAC for ${var.name_prefix} frontend smoke assets"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_function" "spa_rewrite" {
  name    = substr("${var.name_prefix}-spa-rewrite", 0, 64)
  runtime = "cloudfront-js-1.0"
  comment = "Rewrite extensionless frontend routes to index.html"
  publish = true
  code    = <<-JAVASCRIPT
    function handler(event) {
      var request = event.request;
      var uri = request.uri;
      var lastSegment = uri.substring(uri.lastIndexOf('/') + 1);

      if (uri !== '/' && lastSegment.indexOf('.') === -1) {
        request.uri = '/index.html';
      }

      return request;
    }
  JAVASCRIPT
}

# D-034 intentionally defers the formal WAF policy to T059. The ALB origin remains restricted
# to the AWS-managed CloudFront origin-facing prefix list.
#trivy:ignore:AVD-AWS-0011:exp:2026-08-09
resource "aws_cloudfront_distribution" "frontend" {
  enabled             = true
  is_ipv6_enabled     = true
  comment             = "${var.name_prefix} frontend smoke distribution"
  default_root_object = "index.html"
  http_version        = "http2and3"
  price_class         = "PriceClass_200"
  aliases             = var.custom_domain == null ? [] : [var.custom_domain.name]

  origin {
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
    origin_id                = local.static_origin_id
  }

  origin {
    domain_name = var.alb_dns_name
    origin_id   = var.alb_origin_id

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  ordered_cache_behavior {
    path_pattern             = "/api/*"
    target_origin_id         = var.alb_origin_id
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods           = ["GET", "HEAD", "OPTIONS"]
    cache_policy_id          = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad"
    origin_request_policy_id = "b689b0a8-53d0-40ab-baf2-68738e2966ac"
    compress                 = true
  }

  ordered_cache_behavior {
    path_pattern             = "/ws/egress-audio/*"
    target_origin_id         = var.alb_origin_id
    viewer_protocol_policy   = "https-only"
    allowed_methods          = ["GET", "HEAD", "OPTIONS"]
    cached_methods           = ["GET", "HEAD", "OPTIONS"]
    cache_policy_id          = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad"
    origin_request_policy_id = "b689b0a8-53d0-40ab-baf2-68738e2966ac"
    compress                 = false
  }

  ordered_cache_behavior {
    path_pattern           = "/assets/*"
    target_origin_id       = local.static_origin_id
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD", "OPTIONS"]
    cache_policy_id        = "658327ea-f89d-4fab-a63d-7e88639e58f6"
    compress               = true
  }

  default_cache_behavior {
    target_origin_id       = local.static_origin_id
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD", "OPTIONS"]
    cache_policy_id        = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad"
    compress               = true

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_rewrite.arn
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # The default-domain fallback is limited to the bounded D-034 smoke. A configured custom
  # domain uses its existing us-east-1 ACM certificate and the current CloudFront TLS policy.
  #trivy:ignore:AVD-AWS-0010:exp:2026-08-09
  viewer_certificate {
    acm_certificate_arn            = var.custom_domain == null ? null : var.custom_domain.acm_certificate_arn
    cloudfront_default_certificate = var.custom_domain == null
    minimum_protocol_version       = var.custom_domain == null ? "TLSv1" : "TLSv1.2_2021"
    ssl_support_method             = var.custom_domain == null ? null : "sni-only"
  }

  tags = merge(local.resource_tags, {
    Name = "${var.name_prefix}-frontend"
  })

  lifecycle {
    precondition {
      condition     = var.alb_origin_id != local.static_origin_id
      error_message = "alb_origin_id must differ from the module's static S3 origin identifier."
    }

    precondition {
      condition = var.custom_domain == null || (
        try(split(":", var.custom_domain.acm_certificate_arn)[4], "") == data.aws_caller_identity.current.account_id
      )
      error_message = "The custom-domain ACM certificate must belong to the current AWS account."
    }
  }
}

data "aws_iam_policy_document" "frontend" {
  statement {
    sid     = "AllowCloudFrontRead"
    actions = ["s3:GetObject"]
    resources = [
      "${aws_s3_bucket.frontend.arn}/*"
    ]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.frontend.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  policy = data.aws_iam_policy_document.frontend.json

  depends_on = [aws_s3_bucket_public_access_block.frontend]
}
