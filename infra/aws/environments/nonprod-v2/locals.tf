locals {
  name_prefix = "meetingmind-nonprod-v2"
  services    = toset(["bff", "auth", "core", "ai", "realtime-stt"])

  discovery_services = {
    auth         = "auth"
    core         = "core"
    ai           = "ai"
    realtime-stt = "stt"
  }

  runtime_enabled_services = (
    var.enable_runtime_services
    ? var.runtime_enabled_services
    : toset([])
  )

  validation_enabled_services = (
    var.enable_mtls_validation_services
    ? var.mtls_validation_services
    : toset([])
  )

  enabled_services = setunion(
    local.runtime_enabled_services,
    local.validation_enabled_services,
  )

  missing_runtime_image_digests = setsubtract(
    local.runtime_enabled_services,
    toset(keys(var.service_image_digests)),
  )

  missing_validation_image_digests = setsubtract(
    local.validation_enabled_services,
    toset(keys(var.service_image_digests)),
  )

  internal_mtls_enabled = var.enable_mtls_validation_services || var.enable_runtime_services

  tls_bundle_secret_keys = {
    bff          = "bff/tls-bundle"
    auth         = "auth/tls-bundle"
    core         = "core/tls-bundle"
    ai           = "ai/tls-bundle"
    realtime-stt = "stt/tls-bundle"
  }

  tls_service_contracts = {
    bff = {
      service   = "bff"
      spiffe_id = "spiffe://meetingmind.internal/ns/nonprod-v2/sa/meetingmind-bff"
      dns_sans  = []
      ekus      = ["clientAuth"]
    }
    auth = {
      service   = "auth"
      spiffe_id = "spiffe://meetingmind.internal/ns/nonprod-v2/sa/meetingmind-auth"
      dns_sans  = ["auth.meetingmind.internal"]
      ekus      = ["serverAuth"]
    }
    core = {
      service   = "core"
      spiffe_id = "spiffe://meetingmind.internal/ns/nonprod-v2/sa/meetingmind-core"
      dns_sans  = ["core.meetingmind.internal"]
      ekus      = ["clientAuth", "serverAuth"]
    }
    ai = {
      service   = "ai"
      spiffe_id = "spiffe://meetingmind.internal/ns/nonprod-v2/sa/meetingmind-ai"
      dns_sans  = ["ai.meetingmind.internal"]
      ekus      = ["serverAuth"]
    }
    realtime-stt = {
      service   = "stt"
      spiffe_id = "spiffe://meetingmind.internal/ns/nonprod-v2/sa/meetingmind-realtime-stt"
      dns_sans  = ["stt.meetingmind.internal"]
      ekus      = ["serverAuth"]
    }
  }

  cert_loader_image = (
    var.cert_loader_image_digest != null
    ? "${module.ecr.repository_urls["cert-loader"]}@${var.cert_loader_image_digest}"
    : "${module.ecr.repository_urls["cert-loader"]}:${var.image_tag}"
  )

  ai_envoy_image = (
    var.ai_envoy_image_digest != null
    ? "${module.ecr.repository_urls["ai-envoy"]}@${var.ai_envoy_image_digest}"
    : "${module.ecr.repository_urls["ai-envoy"]}:${var.image_tag}"
  )

  common_tags = {
    Project     = "MeetingMind"
    Environment = "nonprod-v2"
    ManagedBy   = "terraform"
    Repository  = "Meeting-Mind/MeetingMind"
  }

  availability_zones = slice(data.aws_availability_zones.available.names, 0, 2)

  secret_descriptions = {
    "auth/db-runtime-password"   = "Auth PostgreSQL runtime role password"
    "auth/db-migration-password" = "Auth PostgreSQL migration role password"
    "auth/refresh-hash"          = "Auth refresh credential HMAC secret"
    "auth/signing-key-ring"      = "Auth KMS signing key ring JSON"
    "core/db-runtime-password"   = "Core PostgreSQL runtime role password"
    "core/livekit-url"           = "LiveKit Cloud WebSocket URL"
    "core/livekit-api-key"       = "LiveKit Cloud API key"
    "core/livekit-api-secret"    = "LiveKit Cloud API secret"
    "ai/database-url"            = "AI PostgreSQL connection URL"
    "ai/openai-api-key"          = "OpenAI API key"
    "stt/db-runtime-password"    = "Realtime STT PostgreSQL runtime role password"
    "stt/egress-ws-secret"       = "One-time LiveKit Egress WebSocket token HMAC secret"
    "stt/soniox-api-key"         = "Soniox API key"
    "stt/openai-api-key"         = "OpenAI API key for STT fallback"
    "stt/debug-token"            = "Realtime STT debug endpoint token"
    "bff/tls-bundle"             = "BFF mTLS certificate, private key, and CA trust bundle JSON"
    "auth/tls-bundle"            = "Auth mTLS certificate, private key, and CA trust bundle JSON"
    "core/tls-bundle"            = "Core mTLS certificate, private key, and CA trust bundle JSON"
    "ai/tls-bundle"              = "AI mTLS certificate, private key, and CA trust bundle JSON"
    "stt/tls-bundle"             = "Realtime STT mTLS certificate, private key, and CA trust bundle JSON"
  }

  service_secret_keys = {
    bff = toset([])
    auth = toset([
      "auth/db-runtime-password",
      "auth/db-migration-password",
      "auth/refresh-hash",
      "auth/signing-key-ring",
    ])
    core = toset([
      "core/db-runtime-password",
      "core/livekit-url",
      "core/livekit-api-key",
      "core/livekit-api-secret",
    ])
    ai = toset([
      "ai/database-url",
      "ai/openai-api-key",
    ])
    realtime-stt = toset([
      "stt/db-runtime-password",
      "stt/egress-ws-secret",
      "stt/soniox-api-key",
      "stt/openai-api-key",
      "stt/debug-token",
      "core/livekit-url",
      "core/livekit-api-key",
      "core/livekit-api-secret",
    ])
  }

  service_definitions = {
    bff = {
      port   = 8081
      cpu    = 512
      memory = 1024
      environment = {
        SPRING_PROFILES_ACTIVE               = local.internal_mtls_enabled ? "nonprod,mtls" : "nonprod"
        BFF_SERVER_PORT                      = "8081"
        BFF_REDIS_HOST                       = module.data.valkey_primary_endpoint
        BFF_REDIS_PORT                       = tostring(module.data.valkey_port)
        SPRING_DATA_REDIS_SSL_ENABLED        = "true"
        SPRING_DATA_REDIS_USERNAME           = module.data.valkey_bff_username
        BFF_AUTH_PROVIDER                    = "auth-service"
        BFF_AUTH_ISSUER                      = "https://auth.meetingmind.internal"
        BFF_AUTH_SERVICE_BASE_URL            = var.internal_service_base_urls.auth
        BFF_CORE_BASE_URL                    = var.internal_service_base_urls.core
        BFF_AI_BASE_URL                      = var.internal_service_base_urls.core
        BFF_LIVEKIT_BASE_URL                 = var.internal_service_base_urls.core
        BFF_TOKEN_VAULT_KEY_PROVIDER         = "kms"
        BFF_TOKEN_VAULT_KMS_KEY_ID           = module.kms.application_key_arn
        BFF_SESSION_COOKIE_SECURE            = "true"
        BFF_ACCEPT_BROWSER_TRAFFIC           = "true"
        BFF_AUTH_ALLOW_TEST_PRINCIPAL_HEADER = "false"
        BFF_CORE_ALLOW_TEST_PRINCIPAL_HEADER = "false"
      }
      secrets = {}
      health_check = [
        "CMD-SHELL",
        "wget -q -O - http://localhost:8081/actuator/health/liveness >/dev/null || exit 1",
      ]
    }
    auth = {
      port   = 8082
      cpu    = 512
      memory = 1024
      environment = {
        SPRING_PROFILES_ACTIVE      = local.internal_mtls_enabled ? "nonprod,mtls" : "nonprod"
        AUTH_SERVER_PORT            = "8082"
        AUTH_DB_URL                 = "jdbc:postgresql://${module.data.rds_address}:${module.data.rds_port}/meetingmind_auth"
        AUTH_DB_MIGRATION_USER      = "meetingmind_auth_migrator"
        AUTH_SIGNING_PROVIDER       = "aws-kms"
        AUTH_SIGNING_ISSUER         = "https://auth.meetingmind.internal"
        AUTH_BFF_SPIFFE_PRINCIPALS  = local.tls_service_contracts.bff.spiffe_id
        AUTH_JWKS_SPIFFE_PRINCIPALS = join(",", [local.tls_service_contracts.bff.spiffe_id, local.tls_service_contracts.core.spiffe_id])
      }
      secrets = {
        AUTH_DB_RUNTIME_PASSWORD   = module.secrets.secret_arns["auth/db-runtime-password"]
        AUTH_DB_MIGRATION_PASSWORD = module.secrets.secret_arns["auth/db-migration-password"]
        AUTH_REFRESH_HASH_SECRET   = module.secrets.secret_arns["auth/refresh-hash"]
        AUTH_SIGNING_KEY_RING_JSON = module.secrets.secret_arns["auth/signing-key-ring"]
      }
      health_check = [
        "CMD-SHELL",
        "wget -q -O - http://localhost:9082/actuator/health/liveness >/dev/null || exit 1",
      ]
    }
    core = {
      port   = 8080
      cpu    = 1024
      memory = 2048
      environment = {
        SPRING_PROFILES_ACTIVE              = local.internal_mtls_enabled ? "db,mtls" : "db"
        SPRING_DATASOURCE_URL               = "jdbc:postgresql://${module.data.rds_address}:${module.data.rds_port}/meetingmind"
        SPRING_DATASOURCE_USERNAME          = "meetingmind_core_app"
        SPRING_FLYWAY_ENABLED               = "false"
        MEETINGMIND_AUTH_VALIDATION_MODE    = "TARGET_ONLY"
        MEETINGMIND_AUTH_TARGET_ISSUER      = "https://auth.meetingmind.internal"
        MEETINGMIND_AUTH_JWKS_URI           = "${var.internal_service_base_urls.auth}/.well-known/jwks.json"
        MEETINGMIND_AI_BASE_URL             = var.internal_service_base_urls.ai
        MEETINGMIND_CORE_ALLOWED_PRINCIPALS = local.tls_service_contracts.bff.spiffe_id
        STT_GATEWAY_MODE                    = "remote"
        MEETINGMIND_STT_BASE_URL            = var.internal_service_base_urls.stt
      }
      secrets = {
        SPRING_DATASOURCE_PASSWORD = module.secrets.secret_arns["core/db-runtime-password"]
        LIVEKIT_WS_URL             = module.secrets.secret_arns["core/livekit-url"]
        LIVEKIT_API_KEY            = module.secrets.secret_arns["core/livekit-api-key"]
        LIVEKIT_API_SECRET         = module.secrets.secret_arns["core/livekit-api-secret"]
      }
      health_check = [
        "CMD-SHELL",
        "wget -q -O - http://localhost:9080/actuator/health/liveness >/dev/null || exit 1",
      ]
    }
    ai = {
      port   = 8000
      cpu    = 1024
      memory = 2048
      environment = {
        AI_TEXT_PROVIDER                    = "openai"
        AI_EMBEDDING_PROVIDER               = "openai"
        OPENAI_MODEL                        = "gpt-4.1-mini"
        OPENAI_EMBEDDING_MODEL              = "text-embedding-3-small"
        OPENAI_EMBEDDING_DIMENSION          = "1536"
        OPENAI_EMBEDDING_INCLUDE_DIMENSIONS = "true"
        AI_VECTOR_DIMENSION                 = "1536"
        AI_INTERNAL_AUTH_MODE               = local.internal_mtls_enabled ? "mtls-proxy" : "shared-token"
        AI_INTERNAL_ALLOWED_SPIFFE_ID       = local.tls_service_contracts.core.spiffe_id
      }
      secrets = {
        AI_DATABASE_URL = module.secrets.secret_arns["ai/database-url"]
        OPENAI_API_KEY  = module.secrets.secret_arns["ai/openai-api-key"]
      }
      health_check = (
        local.internal_mtls_enabled
        ? [
          "CMD-SHELL",
          "python -c \"import urllib.request; urllib.request.urlopen('http://127.0.0.1:8001/health', timeout=3); urllib.request.urlopen('http://127.0.0.1:9901/ready', timeout=3)\" || exit 1",
        ]
        : [
          "CMD-SHELL",
          "python -c \"import urllib.request; urllib.request.urlopen('http://localhost:8000/health', timeout=3)\" || exit 1",
        ]
      )
    }
    realtime-stt = {
      port   = 8083
      cpu    = 1024
      memory = 2048
      environment = {
        SPRING_PROFILES_ACTIVE                      = local.internal_mtls_enabled ? "db,mtls" : "db"
        STT_SERVER_PORT                             = "8083"
        STT_DATASOURCE_URL                          = "jdbc:postgresql://${module.data.rds_address}:${module.data.rds_port}/meetingmind_stt"
        STT_DATASOURCE_USERNAME                     = "meetingmind_stt_app"
        SPRING_FLYWAY_ENABLED                       = "false"
        STT_PROVIDER                                = "soniox-realtime"
        STT_FALLBACK_PROVIDER                       = "openai-realtime"
        STT_DEBUG_AUDIO_DUMP                        = "false"
        MEETINGMIND_STT_ALLOWED_PRINCIPALS          = local.tls_service_contracts.core.spiffe_id
        MEETINGMIND_STT_ALLOW_TEST_PRINCIPAL_HEADER = "false"
      }
      secrets = {
        STT_DATASOURCE_PASSWORD = module.secrets.secret_arns["stt/db-runtime-password"]
        STT_EGRESS_WS_SECRET    = module.secrets.secret_arns["stt/egress-ws-secret"]
        SONIOX_API_KEY          = module.secrets.secret_arns["stt/soniox-api-key"]
        OPENAI_API_KEY          = module.secrets.secret_arns["stt/openai-api-key"]
        STT_DEBUG_TOKEN         = module.secrets.secret_arns["stt/debug-token"]
        LIVEKIT_WS_URL          = module.secrets.secret_arns["core/livekit-url"]
        LIVEKIT_API_KEY         = module.secrets.secret_arns["core/livekit-api-key"]
        LIVEKIT_API_SECRET      = module.secrets.secret_arns["core/livekit-api-secret"]
      }
      health_check = [
        "CMD-SHELL",
        "wget -q -O - http://localhost:9083/actuator/health/liveness >/dev/null || exit 1",
      ]
    }
  }

  service_target_groups = {
    for service, target_groups in {
      bff          = [module.alb.target_group_arns["bff"]]
      realtime-stt = [module.alb.target_group_arns["realtime-stt"]]
    } :
    service => target_groups if !var.enable_mtls_validation_services
  }
}
