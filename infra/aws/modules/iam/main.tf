terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

data "aws_partition" "current" {}

data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  for_each = var.services

  name               = "${var.name_prefix}-${each.key}-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json

  tags = {
    Service = each.key
  }
}

resource "aws_iam_role_policy_attachment" "execution_managed" {
  for_each = var.services

  role       = aws_iam_role.execution[each.key].name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

locals {
  services_with_secrets = {
    for service, secret_arns in var.service_secret_arns :
    service => secret_arns
    if length(secret_arns) > 0
  }
}

data "aws_iam_policy_document" "execution_secrets" {
  for_each = local.services_with_secrets

  statement {
    sid    = "ReadDeclaredSecrets"
    effect = "Allow"
    actions = [
      "secretsmanager:DescribeSecret",
      "secretsmanager:GetSecretValue",
    ]
    resources = each.value
  }

  statement {
    sid    = "DecryptDeclaredKeys"
    effect = "Allow"
    actions = [
      "kms:Decrypt",
      "kms:DescribeKey",
    ]
    resources = var.decrypt_kms_key_arns
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  for_each = local.services_with_secrets

  name   = "read-declared-secrets"
  role   = aws_iam_role.execution[each.key].id
  policy = data.aws_iam_policy_document.execution_secrets[each.key].json
}

resource "aws_iam_role" "task" {
  for_each = var.services

  name               = "${var.name_prefix}-${each.key}-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json

  tags = {
    Service = each.key
  }
}

data "aws_iam_policy_document" "task_tls" {
  for_each = var.service_tls_secret_arns

  statement {
    sid    = "ReadOwnTlsBundle"
    effect = "Allow"
    actions = [
      "secretsmanager:DescribeSecret",
      "secretsmanager:GetSecretValue",
    ]
    resources = [each.value]
  }

  statement {
    sid    = "DecryptTlsBundleKey"
    effect = "Allow"
    actions = [
      "kms:Decrypt",
      "kms:DescribeKey",
    ]
    resources = [var.tls_kms_key_arn]
  }
}

resource "aws_iam_role_policy" "task_tls" {
  for_each = var.service_tls_secret_arns

  name   = "read-own-tls-bundle"
  role   = aws_iam_role.task[each.key].id
  policy = data.aws_iam_policy_document.task_tls[each.key].json
}

data "aws_iam_policy_document" "bff" {
  statement {
    sid    = "TokenVaultEnvelopeEncryption"
    effect = "Allow"
    actions = [
      "kms:Decrypt",
      "kms:DescribeKey",
      "kms:GenerateDataKey",
    ]
    resources = [var.application_kms_key_arn]
  }

  statement {
    sid       = "ConnectToValkey"
    effect    = "Allow"
    actions   = ["elasticache:Connect"]
    resources = [var.valkey_replication_group_arn, var.valkey_user_arn]
  }
}

resource "aws_iam_role_policy" "bff" {
  name   = "bff-runtime"
  role   = aws_iam_role.task["bff"].id
  policy = data.aws_iam_policy_document.bff.json
}

data "aws_iam_policy_document" "auth" {
  statement {
    sid    = "SignAccessTokens"
    effect = "Allow"
    actions = [
      "kms:DescribeKey",
      "kms:GetPublicKey",
      "kms:Sign",
    ]
    resources = [var.jwt_signing_kms_key_arn]
  }
}

resource "aws_iam_role_policy" "auth" {
  name   = "auth-jwt-signing"
  role   = aws_iam_role.task["auth"].id
  policy = data.aws_iam_policy_document.auth.json
}

resource "aws_iam_openid_connect_provider" "github" {
  count = var.enable_github_oidc && var.github_oidc_provider_arn == null ? 1 : 0

  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]

  tags = {
    Service = "platform"
  }
}

locals {
  github_oidc_provider_arn = var.github_oidc_provider_arn != null ? var.github_oidc_provider_arn : try(aws_iam_openid_connect_provider.github[0].arn, null)
}

data "aws_iam_policy_document" "github_assume" {
  count = var.enable_github_oidc ? 1 : 0

  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = var.github_oidc_subjects
    }
  }
}

resource "aws_iam_role" "github_deploy" {
  count = var.enable_github_oidc ? 1 : 0

  name               = "${var.name_prefix}-github-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_assume[0].json

  tags = {
    Service = "platform"
  }

  lifecycle {
    precondition {
      condition     = length(var.github_oidc_subjects) > 0
      error_message = "At least one protected GitHub OIDC subject is required."
    }
  }
}

data "aws_iam_policy_document" "github_deploy" {
  count = var.enable_github_oidc ? 1 : 0

  statement {
    sid    = "EcrLogin"
    effect = "Allow"
    actions = [
      "ecr:GetAuthorizationToken",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "PushImages"
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:CompleteLayerUpload",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = var.ecr_repository_arns
  }

  statement {
    sid    = "DeployEcs"
    effect = "Allow"
    actions = [
      "ecs:DescribeServices",
      "ecs:DescribeTaskDefinition",
      "ecs:RegisterTaskDefinition",
      "ecs:UpdateService",
    ]
    resources = ["*"]
  }

  statement {
    sid     = "PassTaskRoles"
    effect  = "Allow"
    actions = ["iam:PassRole"]
    resources = concat(
      [for role in aws_iam_role.execution : role.arn],
      [for role in aws_iam_role.task : role.arn],
    )
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  count = var.enable_github_oidc ? 1 : 0

  name   = "deploy-meetingmind"
  role   = aws_iam_role.github_deploy[0].id
  policy = data.aws_iam_policy_document.github_deploy[0].json
}
