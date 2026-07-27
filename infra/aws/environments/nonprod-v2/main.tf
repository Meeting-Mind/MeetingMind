resource "terraform_data" "runtime_gate" {
  count = var.enable_runtime_services ? 1 : 0

  lifecycle {
    precondition {
      condition     = var.runtime_gates_acknowledged
      error_message = "Set runtime_gates_acknowledged=true only after completing the private runtime promotion gates in README.md."
    }

    precondition {
      condition     = var.internal_mtls_runtime_verified
      error_message = "Runtime services remain blocked until the T047-D/T048-V AWS mTLS positive/negative and rotation evidence is complete."
    }

    precondition {
      condition     = length(var.runtime_enabled_services) > 0
      error_message = "Select at least one service in runtime_enabled_services for staged runtime enablement."
    }

    precondition {
      condition     = length(local.missing_runtime_image_digests) == 0
      error_message = "Every runtime-enabled service must have an immutable ECR sha256 digest."
    }

    precondition {
      condition     = var.cert_loader_image_digest != null
      error_message = "Runtime services require an immutable meetingmind-cert-loader sha256 digest."
    }

    precondition {
      condition     = !contains(var.runtime_enabled_services, "bff") || length(var.auth_google_client_ids) > 0
      error_message = "A browser-facing BFF runtime requires at least one Auth Google OAuth client ID."
    }

    precondition {
      condition = (
        !var.enable_deployment_smoke ||
        !contains(var.runtime_enabled_services, "realtime-stt") ||
        var.stt_public_ws_base_url != null
      )
      error_message = "Deployment-smoke Realtime STT requires stt_public_ws_base_url for LiveKit Egress audio delivery."
    }

    precondition {
      condition     = !contains(var.runtime_enabled_services, "ai") || var.ai_envoy_image_digest != null
      error_message = "Enabling the AI runtime requires an immutable mirrored ai-envoy sha256 digest."
    }

    precondition {
      condition = alltrue([
        for service in var.runtime_enabled_services :
        lookup(var.service_desired_counts, service, 0) >= 1
      ])
      error_message = "Every runtime-enabled service must have desired count of at least one."
    }
  }
}

resource "terraform_data" "release_gate" {
  lifecycle {
    precondition {
      condition = var.release_gates_acknowledged || (
        !var.enable_autoscaling && (
          (
            !contains(var.runtime_enabled_services, "bff") &&
            !var.enable_http_smoke_listener
            ) || (
            var.enable_deployment_smoke &&
            var.deployment_smoke_gates_acknowledged
          )
        )
      )
      error_message = "BFF/public listeners require either the full release acknowledgement or the bounded deployment smoke gate; autoscaling always requires the full release acknowledgement."
    }
  }
}

resource "terraform_data" "deployment_smoke_gate" {
  lifecycle {
    precondition {
      condition     = var.enable_deployment_smoke == var.deployment_smoke_gates_acknowledged
      error_message = "enable_deployment_smoke and deployment_smoke_gates_acknowledged must be enabled or disabled together."
    }

    precondition {
      condition = !var.enable_deployment_smoke || (
        !var.release_gates_acknowledged &&
        var.enable_runtime_services &&
        var.runtime_enabled_services == local.services &&
        lookup(var.service_desired_counts, "bff", 0) == 1 &&
        var.enable_http_smoke_listener &&
        length(var.allowed_ingress_cidrs) == 0 &&
        !var.enable_autoscaling
      )
      error_message = "Deployment smoke requires release=false, all five runtime services with BFF desired=1, the HTTP origin listener, CloudFront-only ingress, and autoscaling=false."
    }
  }
}

resource "terraform_data" "runtime_selection_gate" {
  lifecycle {
    precondition {
      condition     = var.enable_runtime_services || length(var.runtime_enabled_services) == 0
      error_message = "runtime_enabled_services must be empty while the global runtime kill switch is false."
    }
  }
}

resource "terraform_data" "mtls_mode_exclusion_gate" {
  lifecycle {
    precondition {
      condition     = !(var.enable_mtls_validation_services && var.enable_runtime_services)
      error_message = "Private mTLS validation mode and normal runtime mode cannot be enabled together."
    }

    precondition {
      condition     = var.enable_mtls_validation_services || length(var.mtls_validation_services) == 0
      error_message = "mtls_validation_services must be empty while enable_mtls_validation_services is false."
    }
  }
}

resource "terraform_data" "mtls_validation_gate" {
  count = var.enable_mtls_validation_services ? 1 : 0

  lifecycle {
    precondition {
      condition     = var.internal_mtls_material_ready
      error_message = "Private mTLS validation requires internal_mtls_material_ready bundle/image/local-handshake evidence."
    }

    precondition {
      condition     = length(var.mtls_validation_services) > 0
      error_message = "Select at least one private service in mtls_validation_services."
    }

    precondition {
      condition     = length(local.missing_validation_image_digests) == 0
      error_message = "Every mTLS validation service must have an immutable ECR sha256 digest."
    }

    precondition {
      condition     = var.cert_loader_image_digest != null
      error_message = "Private mTLS validation requires an immutable meetingmind-cert-loader sha256 digest."
    }

    precondition {
      condition     = !contains(var.mtls_validation_services, "ai") || var.ai_envoy_image_digest != null
      error_message = "Validating the AI service requires an immutable mirrored ai-envoy sha256 digest."
    }

    precondition {
      condition     = !var.enable_http_smoke_listener
      error_message = "Private mTLS validation must run without any public ALB listener."
    }

    precondition {
      condition = alltrue([
        for service in var.mtls_validation_services :
        lookup(var.service_desired_counts, service, 0) >= 1
      ])
      error_message = "Every mTLS validation service must have desired count of at least one."
    }
  }
}

resource "terraform_data" "smoke_gate" {
  count = var.enable_http_smoke_listener ? 1 : 0

  lifecycle {
    precondition {
      condition = (
        var.enable_deployment_smoke ||
        (length(var.allowed_ingress_cidrs) > 0 && alltrue([for cidr in var.allowed_ingress_cidrs : cidr != "0.0.0.0/0"]))
      )
      error_message = "The HTTP smoke listener requires the CloudFront deployment smoke mode or at least one restricted operator CIDR; 0.0.0.0/0 is forbidden."
    }
  }
}

module "kms" {
  source = "../../modules/kms"

  name_prefix = local.name_prefix
}

module "network" {
  source = "../../modules/network"

  name_prefix          = local.name_prefix
  vpc_cidr             = var.vpc_cidr
  availability_zones   = local.availability_zones
  public_subnet_cidrs  = var.public_subnet_cidrs
  private_subnet_cidrs = var.private_subnet_cidrs
  data_subnet_cidrs    = var.data_subnet_cidrs
  logs_kms_key_arn     = module.kms.logs_key_arn
}

module "service_discovery" {
  source = "../../modules/service-discovery"

  name_prefix    = local.name_prefix
  namespace_name = "meetingmind.internal"
  vpc_id         = module.network.vpc_id
  services       = local.discovery_services
}

module "security" {
  source = "../../modules/security"

  name_prefix                      = local.name_prefix
  aws_region                       = var.aws_region
  vpc_id                           = module.network.vpc_id
  allowed_ingress_cidrs            = var.allowed_ingress_cidrs
  enable_http_smoke_listener       = var.enable_http_smoke_listener
  enable_cloudfront_origin_ingress = var.enable_deployment_smoke
  enable_https_listener            = false
}

module "vpc_endpoints" {
  source = "../../modules/vpc-endpoints"

  name_prefix             = local.name_prefix
  aws_region              = var.aws_region
  vpc_id                  = module.network.vpc_id
  private_subnet_ids      = module.network.private_subnet_ids
  private_route_table_ids = module.network.private_route_table_ids
  data_route_table_ids    = module.network.data_route_table_ids
  security_group_id       = module.security.endpoints_security_group_id
}

module "secrets" {
  source = "../../modules/secrets"

  name_prefix = local.name_prefix
  kms_key_arn = module.kms.data_key_arn
  secrets     = local.secret_descriptions
  tls_bundle_read_principals = {
    for service, secret_key in local.tls_bundle_secret_keys :
    secret_key => module.iam.task_role_arns[service]
  }
}

module "data" {
  source = "../../modules/data"

  name_prefix                = local.name_prefix
  data_subnet_ids            = module.network.data_subnet_ids
  database_security_group_id = module.security.database_security_group_id
  cache_security_group_id    = module.security.cache_security_group_id
  data_kms_key_arn           = module.kms.data_key_arn
  rds_instance_class         = var.rds_instance_class
  valkey_node_type           = var.valkey_node_type
}

module "ecr" {
  source = "../../modules/ecr"

  name_prefix  = local.name_prefix
  repositories = setunion(local.services, ["cert-loader", "ai-envoy"])
  kms_key_arn  = module.kms.data_key_arn
}

module "iam" {
  source = "../../modules/iam"

  name_prefix = local.name_prefix
  services    = local.services
  service_secret_arns = {
    for service, secret_keys in local.service_secret_keys :
    service => toset([
      for secret_key in secret_keys :
      module.secrets.secret_arns[secret_key]
    ])
  }
  service_tls_secret_arns = {
    for service, secret_key in local.tls_bundle_secret_keys :
    service => module.secrets.secret_arns[secret_key]
  }
  tls_kms_key_arn              = module.kms.data_key_arn
  decrypt_kms_key_arns         = [module.kms.data_key_arn]
  application_kms_key_arn      = module.kms.application_key_arn
  jwt_signing_kms_key_arn      = module.kms.jwt_signing_key_arn
  valkey_replication_group_arn = module.data.valkey_replication_group_arn
  valkey_user_arn              = module.data.valkey_bff_user_arn
  ecr_repository_arns          = values(module.ecr.repository_arns)
  enable_github_oidc           = var.enable_github_oidc
  github_oidc_provider_arn     = var.github_oidc_provider_arn
  github_repository            = var.github_repository
  github_oidc_subjects         = var.github_oidc_subjects
}

module "ecs_cluster" {
  source = "../../modules/ecs-cluster"

  name = local.name_prefix
}

module "alb" {
  source = "../../modules/alb"

  name_prefix                = local.name_prefix
  vpc_id                     = module.network.vpc_id
  public_subnet_ids          = module.network.public_subnet_ids
  security_group_id          = module.security.alb_security_group_id
  enable_http_smoke_listener = var.enable_http_smoke_listener
  enable_stt_smoke_route     = var.enable_deployment_smoke || var.release_gates_acknowledged

  depends_on = [
    terraform_data.deployment_smoke_gate,
    terraform_data.release_gate,
    terraform_data.smoke_gate,
  ]
}

module "frontend_edge" {
  count  = var.enable_deployment_smoke ? 1 : 0
  source = "../../modules/frontend-edge"

  name_prefix   = local.name_prefix
  alb_dns_name  = module.alb.dns_name
  alb_origin_id = "${local.name_prefix}-bff-alb"
  custom_domain = var.frontend_custom_domain
  tags          = local.common_tags

  depends_on = [
    terraform_data.deployment_smoke_gate,
    terraform_data.release_gate,
    terraform_data.smoke_gate,
  ]
}

module "observability" {
  source = "../../modules/observability"

  name_prefix                 = local.name_prefix
  aws_region                  = var.aws_region
  services                    = local.services
  logs_kms_key_arn            = module.kms.logs_key_arn
  alarm_email                 = var.alarm_email
  ecs_cluster_name            = module.ecs_cluster.name
  alb_arn_suffix              = module.alb.arn_suffix
  target_group_arn_suffixes   = module.alb.target_group_arn_suffixes
  rds_identifier              = module.data.rds_identifier
  valkey_replication_group_id = module.data.valkey_replication_group_id
  nat_gateway_id              = module.network.regional_nat_gateway_id
}

module "task_definition" {
  for_each = local.service_definitions
  source   = "../../modules/ecs-task"

  family         = "${local.name_prefix}-${each.key}"
  container_name = each.key
  image = (
    contains(keys(var.service_image_digests), each.key)
    ? "${module.ecr.repository_urls[each.key]}@${var.service_image_digests[each.key]}"
    : "${module.ecr.repository_urls[each.key]}:${var.image_tag}"
  )
  container_port       = each.value.port
  cpu                  = each.value.cpu
  memory               = each.value.memory
  execution_role_arn   = module.iam.execution_role_arns[each.key]
  task_role_arn        = module.iam.task_role_arns[each.key]
  aws_region           = var.aws_region
  log_group_name       = module.observability.log_group_names[each.key]
  environment          = each.value.environment
  secrets              = each.value.secrets
  health_check_command = each.value.health_check
  tls_bundle = !local.internal_mtls_enabled ? null : {
    loader_image       = local.cert_loader_image
    secret_arn         = module.secrets.secret_arns[local.tls_bundle_secret_keys[each.key]]
    expected_service   = local.tls_service_contracts[each.key].service
    expected_spiffe_id = local.tls_service_contracts[each.key].spiffe_id
    expected_dns_sans  = local.tls_service_contracts[each.key].dns_sans
    expected_ekus      = local.tls_service_contracts[each.key].ekus
  }
  envoy_sidecar = (
    each.key == "ai" && local.internal_mtls_enabled
    ? { image = local.ai_envoy_image }
    : null
  )
  container_command = (
    each.key == "ai" && local.internal_mtls_enabled
    ? ["uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", "8001"]
    : []
  )
  background_workers = (
    each.key == "ai"
    ? { ai-worker = ["python", "-m", "app.embedding_worker"] }
    : {}
  )
}

module "service" {
  for_each = local.service_definitions
  source   = "../../modules/ecs-service"

  enabled              = contains(local.enabled_services, each.key)
  name                 = "${local.name_prefix}-${each.key}"
  cluster_arn          = module.ecs_cluster.arn
  cluster_name         = module.ecs_cluster.name
  task_definition_arn  = module.task_definition[each.key].arn
  desired_count        = lookup(var.service_desired_counts, each.key, 0)
  subnet_ids           = module.network.private_subnet_ids
  security_group_id    = module.security.service_security_group_ids[each.key]
  container_name       = each.key
  container_port       = each.value.port
  target_groups        = lookup(local.service_target_groups, each.key, [])
  service_registry_arn = try(module.service_discovery.service_arns[each.key], null)
  enable_autoscaling   = var.enable_autoscaling

  depends_on = [
    module.vpc_endpoints,
    terraform_data.deployment_smoke_gate,
    terraform_data.release_gate,
    terraform_data.runtime_gate,
    terraform_data.runtime_selection_gate,
    terraform_data.mtls_mode_exclusion_gate,
    terraform_data.mtls_validation_gate,
  ]
}
