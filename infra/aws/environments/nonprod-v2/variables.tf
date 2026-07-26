variable "aws_region" {
  description = "AWS region for NonProd V2."
  type        = string
  default     = "ap-northeast-2"

  validation {
    condition     = var.aws_region == "ap-northeast-2"
    error_message = "MeetingMind NonProd V2 is fixed to ap-northeast-2."
  }
}

variable "expected_aws_account_id" {
  description = "AWS account ID allowed by the provider."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.expected_aws_account_id))
    error_message = "expected_aws_account_id must be a 12-digit AWS account ID."
  }
}

variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "public_subnet_cidrs" {
  type    = list(string)
  default = ["10.20.0.0/24", "10.20.1.0/24"]
}

variable "private_subnet_cidrs" {
  type    = list(string)
  default = ["10.20.16.0/20", "10.20.32.0/20"]
}

variable "data_subnet_cidrs" {
  type    = list(string)
  default = ["10.20.48.0/24", "10.20.49.0/24"]
}

variable "allowed_ingress_cidrs" {
  description = "Restricted operator public CIDRs for temporary HTTP smoke access."
  type        = list(string)
  default     = []
}

variable "enable_http_smoke_listener" {
  description = "Creates an HTTP listener for restricted infrastructure smoke tests only."
  type        = bool
  default     = false
}

variable "enable_runtime_services" {
  description = "Global ECS runtime kill switch. Keep false until private runtime images, secret versions, DB bootstrap, and T047 prerequisites are ready."
  type        = bool
  default     = false
}

variable "runtime_enabled_services" {
  description = "Explicit staged allowlist used only when enable_runtime_services is true."
  type        = set(string)
  default     = []

  validation {
    condition = length(setsubtract(
      var.runtime_enabled_services,
      toset(["bff", "auth", "core", "ai", "realtime-stt"]),
    )) == 0
    error_message = "runtime_enabled_services contains an unknown MeetingMind service."
  }
}

variable "runtime_gates_acknowledged" {
  description = "Explicit acknowledgement that the private runtime promotion gates in README.md are complete. This does not authorize BFF, public listeners, or autoscaling."
  type        = bool
  default     = false
}

variable "release_gates_acknowledged" {
  description = "Explicit acknowledgement that Q-013, BFF Valkey IAM, T047-E, T048, and T049 release gates are complete before BFF, public listeners, or autoscaling are enabled."
  type        = bool
  default     = false
}

variable "enable_deployment_smoke" {
  description = "Temporarily enables the NonProd browser smoke path without claiming the full BFF/public release gates are complete."
  type        = bool
  default     = false
}

variable "deployment_smoke_gates_acknowledged" {
  description = "Acknowledges only the bounded BFF one-task, Valkey IAM/TLS, CloudFront-default-domain deployment smoke contract."
  type        = bool
  default     = false
}

variable "internal_mtls_material_ready" {
  description = "Explicit T047-B evidence: TLS bundle versions, scanned image digests, loader/Envoy config, and the local mTLS handshake matrix are complete."
  type        = bool
  default     = false
}

variable "internal_mtls_runtime_verified" {
  description = "Explicit T047-D/T048-V evidence: the AWS positive/negative matrix and rotation/rollback drills passed in the private validation deployment."
  type        = bool
  default     = false
}

variable "enable_mtls_validation_services" {
  description = "Starts only the private mTLS validation allowlist without public BFF/ALB traffic. Mutually exclusive with enable_runtime_services."
  type        = bool
  default     = false
}

variable "mtls_validation_services" {
  description = "Private mTLS validation allowlist staged in Auth, AI/STT, Core order. BFF stays out of private validation."
  type        = set(string)
  default     = []

  validation {
    condition = length(setsubtract(
      var.mtls_validation_services,
      toset(["auth", "core", "ai", "realtime-stt"]),
    )) == 0
    error_message = "mtls_validation_services allows only auth, core, ai, and realtime-stt."
  }
}

variable "cert_loader_image_digest" {
  description = "Immutable ECR sha256 digest for meetingmind-cert-loader. Required whenever validation or runtime services start."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.cert_loader_image_digest == null || can(regex("^sha256:[0-9a-f]{64}$", var.cert_loader_image_digest))
    error_message = "cert_loader_image_digest must be sha256:<64 lowercase hex>."
  }
}

variable "ai_envoy_image_digest" {
  description = "Immutable mirrored ECR sha256 digest for the AI Envoy sidecar. Required whenever the AI service starts."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.ai_envoy_image_digest == null || can(regex("^sha256:[0-9a-f]{64}$", var.ai_envoy_image_digest))
    error_message = "ai_envoy_image_digest must be sha256:<64 lowercase hex>."
  }
}

variable "enable_autoscaling" {
  description = "Enables ECS target tracking after Q-013 SLO values are approved."
  type        = bool
  default     = false
}

variable "image_tag" {
  description = "Foundation-only image tag. Runtime-enabled services must use service_image_digests."
  type        = string
  default     = "bootstrap"

  validation {
    condition     = can(regex("^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$", var.image_tag))
    error_message = "image_tag must be a valid ECR tag."
  }
}

variable "service_image_digests" {
  description = "Service-specific immutable ECR sha256 digests used by runtime Task Definitions."
  type        = map(string)
  default     = {}

  validation {
    condition = (
      length(setsubtract(
        toset(keys(var.service_image_digests)),
        toset(["bff", "auth", "core", "ai", "realtime-stt"]),
      )) == 0
      && alltrue([
        for digest in values(var.service_image_digests) :
        can(regex("^sha256:[0-9a-f]{64}$", digest))
      ])
    )
    error_message = "service_image_digests must contain only known services and sha256:<64 lowercase hex> values."
  }
}

variable "service_desired_counts" {
  description = "Per-service desired count. Q-013 must approve final Multi-AZ values."
  type        = map(number)
  default = {
    bff          = 1
    auth         = 1
    core         = 1
    ai           = 1
    realtime-stt = 1
  }

  validation {
    condition = (
      length(setsubtract(
        toset(keys(var.service_desired_counts)),
        toset(["bff", "auth", "core", "ai", "realtime-stt"]),
      )) == 0
      && alltrue([
        for count in values(var.service_desired_counts) :
        count >= 0 && floor(count) == count
      ])
    )
    error_message = "service_desired_counts must contain only known services and non-negative integers."
  }
}

variable "alarm_email" {
  description = "Optional email endpoint for SNS alarms. The subscription requires confirmation."
  type        = string
  default     = null
  nullable    = true
}

variable "rds_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "valkey_node_type" {
  type    = string
  default = "cache.t4g.micro"
}

variable "enable_github_oidc" {
  description = "Creates the GitHub deployment role. Confirm account-level OIDC provider ownership first."
  type        = bool
  default     = false
}

variable "github_oidc_provider_arn" {
  description = "Existing account-level GitHub OIDC provider ARN, if present."
  type        = string
  default     = null
  nullable    = true
}

variable "github_repository" {
  type    = string
  default = "Meeting-Mind/MeetingMind"
}

variable "github_oidc_subjects" {
  description = "Protected GitHub branch or environment OIDC subjects allowed to deploy."
  type        = list(string)
  default     = []
}

variable "internal_service_base_urls" {
  description = "Fixed Cloud Map HTTPS service addresses. Runtime mTLS wiring is completed in T047."
  type = object({
    auth = string
    core = string
    ai   = string
    stt  = string
  })
  default = {
    auth = "https://auth.meetingmind.internal:8082"
    core = "https://core.meetingmind.internal:8080"
    ai   = "https://ai.meetingmind.internal:8000"
    stt  = "https://stt.meetingmind.internal:8083"
  }

  validation {
    condition = var.internal_service_base_urls == {
      auth = "https://auth.meetingmind.internal:8082"
      core = "https://core.meetingmind.internal:8080"
      ai   = "https://ai.meetingmind.internal:8000"
      stt  = "https://stt.meetingmind.internal:8083"
    }
    error_message = "Internal service URLs must use the approved Cloud Map HTTPS names and ports."
  }
}
