terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

resource "aws_secretsmanager_secret" "this" {
  for_each = var.secrets

  name                    = "/${var.name_prefix}/${each.key}"
  description             = each.value
  kms_key_id              = var.kms_key_arn
  recovery_window_in_days = 7

  tags = {
    Name    = "${var.name_prefix}-${replace(each.key, "/", "-")}"
    Service = split("/", each.key)[0]
  }
}

data "aws_iam_policy_document" "tls_read_deny" {
  for_each = var.tls_bundle_read_principals

  statement {
    sid    = "DenyNonServiceTlsBundleRead"
    effect = "Deny"

    principals {
      type        = "AWS"
      identifiers = ["*"]
    }

    actions   = ["secretsmanager:GetSecretValue"]
    resources = ["*"]

    condition {
      test     = "StringNotEquals"
      variable = "aws:PrincipalArn"
      values   = [each.value]
    }
  }
}

resource "aws_secretsmanager_secret_policy" "tls_read_deny" {
  for_each = var.tls_bundle_read_principals

  secret_arn = aws_secretsmanager_secret.this[each.key].arn
  policy     = data.aws_iam_policy_document.tls_read_deny[each.key].json
}
