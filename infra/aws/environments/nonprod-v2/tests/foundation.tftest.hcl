mock_provider "aws" {}

override_data {
  target = data.aws_availability_zones.available
  values = {
    names = ["ap-northeast-2a", "ap-northeast-2c"]
  }
}

override_data {
  target = module.kms.data.aws_iam_policy_document.logs
  values = {
    json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
  }
}

override_data {
  target = module.network.data.aws_iam_policy_document.flow_logs_assume
  values = {
    json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
  }
}

override_data {
  target = module.network.data.aws_iam_policy_document.flow_logs
  values = {
    json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
  }
}

override_data {
  target = module.iam.data.aws_iam_policy_document.ecs_tasks_assume
  values = {
    json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
  }
}

override_data {
  target = module.iam.data.aws_partition.current
  values = {
    partition = "aws"
  }
}

override_data {
  target = module.iam.data.aws_iam_policy_document.execution_secrets
  values = {
    json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
  }
}

override_data {
  target = module.iam.data.aws_iam_policy_document.task_tls
  values = {
    json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
  }
}

override_data {
  target = module.secrets.data.aws_iam_policy_document.tls_read_deny
  values = {
    json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
  }
}

override_data {
  target = module.iam.data.aws_iam_policy_document.bff
  values = {
    json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
  }
}

override_data {
  target = module.iam.data.aws_iam_policy_document.auth
  values = {
    json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
  }
}

override_data {
  target = module.security.data.aws_ec2_managed_prefix_list.cloudfront_origin_facing
  values = {
    id = "pl-cloudfront-origin"
  }
}

override_data {
  target = module.security.data.aws_ec2_managed_prefix_list.s3
  values = {
    id = "pl-regional-s3"
  }
}

override_data {
  target = module.frontend_edge.data.aws_caller_identity.current
  values = {
    account_id = "123456789012"
  }
}

override_data {
  target = module.frontend_edge.data.aws_partition.current
  values = {
    partition = "aws"
  }
}

override_data {
  target = module.frontend_edge.data.aws_iam_policy_document.frontend
  values = {
    json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
  }
}

run "foundation_plan" {
  command = plan

  variables {
    expected_aws_account_id = "123456789012"
  }

  assert {
    condition     = output.runtime_services_enabled == false
    error_message = "Foundation plans must keep ECS runtime services disabled by default."
  }

  assert {
    condition     = length(output.public_subnet_ids) == 2 && length(output.private_subnet_ids) == 2 && length(output.data_subnet_ids) == 2
    error_message = "NonProd V2 must create two public, private application, and data subnets."
  }

  assert {
    condition = output.service_discovery_fqdns == {
      auth         = "auth.meetingmind.internal"
      core         = "core.meetingmind.internal"
      ai           = "ai.meetingmind.internal"
      realtime-stt = "stt.meetingmind.internal"
    }
    error_message = "Cloud Map must expose only the approved private service names."
  }

  assert {
    condition     = length(output.execution_role_arns) == 5
    error_message = "Each service must have a distinct ECS execution role."
  }

  assert {
    condition = alltrue([
      for secret_key in ["bff/tls-bundle", "auth/tls-bundle", "core/tls-bundle", "ai/tls-bundle", "stt/tls-bundle"] :
      contains(keys(output.application_secret_names), secret_key)
    ])
    error_message = "Each service must have a value-less TLS bundle secret container."
  }

  assert {
    condition     = output.mtls_validation_services_enabled == false && length(output.mtls_validation_enabled_services) == 0
    error_message = "Foundation plans must keep private mTLS validation services disabled by default."
  }

  assert {
    condition = alltrue([
      for service, container_names in output.task_definition_container_names :
      container_names == [service]
    ])
    error_message = "Foundation task definitions must not wire the cert-loader before an mTLS mode is enabled."
  }
}

run "public_smoke_requires_restricted_cidr" {
  command = plan

  variables {
    expected_aws_account_id    = "123456789012"
    enable_http_smoke_listener = true
    allowed_ingress_cidrs      = []
    release_gates_acknowledged = true
  }

  expect_failures = [terraform_data.smoke_gate]
}

run "public_smoke_requires_release_acknowledgement" {
  command = plan

  variables {
    expected_aws_account_id    = "123456789012"
    enable_http_smoke_listener = true
    allowed_ingress_cidrs      = ["203.0.113.10/32"]
  }

  expect_failures = [terraform_data.release_gate]
}

run "targeted_public_smoke_requires_release_acknowledgement" {
  command = plan

  variables {
    expected_aws_account_id    = "123456789012"
    enable_http_smoke_listener = true
    allowed_ingress_cidrs      = ["203.0.113.10/32"]
  }

  plan_options {
    target = [module.alb.aws_lb_listener.http_smoke[0]]
  }

  expect_failures = [terraform_data.release_gate]
}

run "deployment_smoke_requires_matching_acknowledgement" {
  command = plan

  variables {
    expected_aws_account_id = "123456789012"
    enable_deployment_smoke = true
  }

  plan_options {
    target = [terraform_data.deployment_smoke_gate]
  }

  expect_failures = [terraform_data.deployment_smoke_gate]
}

run "deployment_smoke_acknowledgement_cannot_be_stale" {
  command = plan

  variables {
    expected_aws_account_id             = "123456789012"
    deployment_smoke_gates_acknowledged = true
  }

  plan_options {
    target = [terraform_data.deployment_smoke_gate]
  }

  expect_failures = [terraform_data.deployment_smoke_gate]
}

run "deployment_smoke_rejects_operator_cidr" {
  command = plan

  variables {
    expected_aws_account_id             = "123456789012"
    enable_deployment_smoke             = true
    deployment_smoke_gates_acknowledged = true
    enable_runtime_services             = true
    runtime_enabled_services            = ["bff", "auth", "core", "ai", "realtime-stt"]
    runtime_gates_acknowledged          = true
    internal_mtls_runtime_verified      = true
    enable_http_smoke_listener          = true
    allowed_ingress_cidrs               = ["203.0.113.10/32"]
  }

  plan_options {
    target = [terraform_data.deployment_smoke_gate]
  }

  expect_failures = [terraform_data.deployment_smoke_gate]
}

run "deployment_smoke_plan" {
  command = plan

  variables {
    expected_aws_account_id             = "123456789012"
    enable_deployment_smoke             = true
    deployment_smoke_gates_acknowledged = true
    enable_runtime_services             = true
    runtime_enabled_services            = ["bff", "auth", "core", "ai", "realtime-stt"]
    runtime_gates_acknowledged          = true
    internal_mtls_runtime_verified      = true
    enable_http_smoke_listener          = true
    cert_loader_image_digest            = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    ai_envoy_image_digest               = "sha256:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    service_image_digests = {
      bff          = "sha256:9999999999999999999999999999999999999999999999999999999999999999"
      auth         = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      core         = "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
      ai           = "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
      realtime-stt = "sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
    }
  }

  assert {
    condition     = output.deployment_smoke_enabled && !var.release_gates_acknowledged && !var.enable_autoscaling
    error_message = "Deployment smoke must not claim the full release or autoscaling gates."
  }

  assert {
    condition     = output.service_target_group_attachment_counts["bff"] == 1
    error_message = "Deployment smoke must attach only the BFF service to the ALB target group."
  }

  assert {
    condition     = output.service_target_group_attachment_counts["realtime-stt"] == 0
    error_message = "Deployment smoke must not expose the Realtime STT websocket target."
  }

  assert {
    condition     = !output.stt_public_smoke_route_enabled
    error_message = "Deployment smoke must keep the public STT listener rule disabled."
  }
}

run "targeted_deployment_smoke_cloudfront_plan" {
  command = plan

  variables {
    expected_aws_account_id             = "123456789012"
    enable_deployment_smoke             = true
    deployment_smoke_gates_acknowledged = true
    enable_runtime_services             = true
    runtime_enabled_services            = ["bff", "auth", "core", "ai", "realtime-stt"]
    runtime_gates_acknowledged          = true
    internal_mtls_runtime_verified      = true
    enable_http_smoke_listener          = true
    cert_loader_image_digest            = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    ai_envoy_image_digest               = "sha256:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    service_image_digests = {
      bff          = "sha256:9999999999999999999999999999999999999999999999999999999999999999"
      auth         = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      core         = "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
      ai           = "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
      realtime-stt = "sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
    }
  }

  plan_options {
    target = [module.frontend_edge[0].aws_cloudfront_distribution.frontend]
  }
}

run "autoscaling_requires_release_acknowledgement" {
  command = plan

  variables {
    expected_aws_account_id = "123456789012"
    enable_autoscaling      = true
  }

  expect_failures = [terraform_data.release_gate]
}

run "runtime_requires_acknowledgement" {
  command = plan

  variables {
    expected_aws_account_id    = "123456789012"
    enable_runtime_services    = true
    runtime_gates_acknowledged = false
  }

  expect_failures = [terraform_data.runtime_gate]
}

run "runtime_selection_requires_global_switch" {
  command = plan

  variables {
    expected_aws_account_id  = "123456789012"
    runtime_enabled_services = ["auth"]
  }

  expect_failures = [terraform_data.runtime_selection_gate]
}

run "runtime_requires_image_digest" {
  command = plan

  variables {
    expected_aws_account_id        = "123456789012"
    enable_runtime_services        = true
    runtime_enabled_services       = ["auth"]
    runtime_gates_acknowledged     = true
    internal_mtls_runtime_verified = true
    cert_loader_image_digest       = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
  }

  expect_failures = [terraform_data.runtime_gate]
}

run "staged_auth_runtime_plan" {
  command = plan

  variables {
    expected_aws_account_id        = "123456789012"
    enable_runtime_services        = true
    runtime_enabled_services       = ["auth"]
    runtime_gates_acknowledged     = true
    internal_mtls_runtime_verified = true
    cert_loader_image_digest       = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    service_image_digests = {
      auth = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
  }

  assert {
    condition     = output.runtime_enabled_services == toset(["auth"])
    error_message = "A verified private Auth runtime must not require the BFF/public release acknowledgement."
  }
}

run "targeted_private_auth_runtime_plan" {
  command = plan

  variables {
    expected_aws_account_id        = "123456789012"
    enable_runtime_services        = true
    runtime_enabled_services       = ["auth"]
    runtime_gates_acknowledged     = true
    internal_mtls_runtime_verified = true
    cert_loader_image_digest       = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    service_image_digests = {
      auth = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
  }

  plan_options {
    target = [module.service["auth"].aws_ecs_service.this[0]]
  }
}

run "bff_runtime_requires_release_acknowledgement" {
  command = plan

  variables {
    expected_aws_account_id        = "123456789012"
    enable_runtime_services        = true
    runtime_enabled_services       = ["bff"]
    runtime_gates_acknowledged     = true
    internal_mtls_runtime_verified = true
    cert_loader_image_digest       = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    service_image_digests = {
      bff = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
  }

  expect_failures = [terraform_data.release_gate]
}

run "targeted_bff_runtime_requires_release_acknowledgement" {
  command = plan

  variables {
    expected_aws_account_id        = "123456789012"
    enable_runtime_services        = true
    runtime_enabled_services       = ["bff"]
    runtime_gates_acknowledged     = true
    internal_mtls_runtime_verified = true
    cert_loader_image_digest       = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    service_image_digests = {
      bff = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
  }

  plan_options {
    target = [module.service["bff"].aws_ecs_service.this[0]]
  }

  expect_failures = [terraform_data.release_gate]
}

run "targeted_autoscaling_requires_release_acknowledgement" {
  command = plan

  variables {
    expected_aws_account_id        = "123456789012"
    enable_runtime_services        = true
    runtime_enabled_services       = ["auth"]
    runtime_gates_acknowledged     = true
    internal_mtls_runtime_verified = true
    cert_loader_image_digest       = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    enable_autoscaling             = true
    service_image_digests = {
      auth = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
  }

  plan_options {
    target = [module.service["auth"].aws_appautoscaling_target.this[0]]
  }

  expect_failures = [terraform_data.release_gate]
}

run "release_acknowledgement_allows_bff_runtime_plan" {
  command = plan

  variables {
    expected_aws_account_id        = "123456789012"
    enable_runtime_services        = true
    runtime_enabled_services       = ["bff"]
    runtime_gates_acknowledged     = true
    release_gates_acknowledged     = true
    internal_mtls_runtime_verified = true
    cert_loader_image_digest       = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    service_image_digests = {
      bff = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
  }

  assert {
    condition     = output.runtime_enabled_services == toset(["bff"])
    error_message = "An explicit full release acknowledgement must unlock the BFF runtime plan."
  }
}

run "runtime_requires_verified_mtls_evidence" {
  command = plan

  variables {
    expected_aws_account_id    = "123456789012"
    enable_runtime_services    = true
    runtime_enabled_services   = ["auth"]
    runtime_gates_acknowledged = true
    cert_loader_image_digest   = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    service_image_digests = {
      auth = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
  }

  expect_failures = [terraform_data.runtime_gate]
}

run "runtime_requires_cert_loader_digest" {
  command = plan

  variables {
    expected_aws_account_id        = "123456789012"
    enable_runtime_services        = true
    runtime_enabled_services       = ["auth"]
    runtime_gates_acknowledged     = true
    internal_mtls_runtime_verified = true
    service_image_digests = {
      auth = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
  }

  expect_failures = [terraform_data.runtime_gate]
}

run "validation_requires_material_evidence" {
  command = plan

  variables {
    expected_aws_account_id         = "123456789012"
    enable_mtls_validation_services = true
    mtls_validation_services        = ["auth"]
    internal_mtls_material_ready    = false
    cert_loader_image_digest        = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    service_image_digests = {
      auth = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
  }

  expect_failures = [terraform_data.mtls_validation_gate]
}

run "validation_requires_image_digests" {
  command = plan

  variables {
    expected_aws_account_id         = "123456789012"
    enable_mtls_validation_services = true
    mtls_validation_services        = ["auth"]
    internal_mtls_material_ready    = true
    cert_loader_image_digest        = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
  }

  expect_failures = [terraform_data.mtls_validation_gate]
}

run "validation_rejects_public_smoke_listener" {
  command = plan

  variables {
    expected_aws_account_id         = "123456789012"
    enable_mtls_validation_services = true
    mtls_validation_services        = ["auth"]
    internal_mtls_material_ready    = true
    cert_loader_image_digest        = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    enable_http_smoke_listener      = true
    release_gates_acknowledged      = true
    allowed_ingress_cidrs           = ["203.0.113.10/32"]
    service_image_digests = {
      auth = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
  }

  expect_failures = [terraform_data.mtls_validation_gate]
}

run "validation_rejects_bff" {
  command = plan

  variables {
    expected_aws_account_id         = "123456789012"
    enable_mtls_validation_services = true
    mtls_validation_services        = ["bff"]
    internal_mtls_material_ready    = true
    cert_loader_image_digest        = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
  }

  expect_failures = [var.mtls_validation_services]
}

run "validation_selection_requires_validation_switch" {
  command = plan

  variables {
    expected_aws_account_id  = "123456789012"
    mtls_validation_services = ["auth"]
  }

  expect_failures = [terraform_data.mtls_mode_exclusion_gate]
}

run "validation_and_runtime_are_mutually_exclusive" {
  command = plan

  variables {
    expected_aws_account_id         = "123456789012"
    enable_runtime_services         = true
    runtime_enabled_services        = ["auth"]
    runtime_gates_acknowledged      = true
    internal_mtls_runtime_verified  = true
    enable_mtls_validation_services = true
    mtls_validation_services        = ["auth"]
    internal_mtls_material_ready    = true
    cert_loader_image_digest        = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    service_image_digests = {
      auth = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
  }

  expect_failures = [terraform_data.mtls_mode_exclusion_gate]
}

run "validation_of_ai_requires_envoy_digest" {
  command = plan

  variables {
    expected_aws_account_id         = "123456789012"
    enable_mtls_validation_services = true
    mtls_validation_services        = ["ai"]
    internal_mtls_material_ready    = true
    cert_loader_image_digest        = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    service_image_digests = {
      ai = "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
    }
  }

  expect_failures = [terraform_data.mtls_validation_gate]
}

run "private_validation_plan" {
  command = plan

  variables {
    expected_aws_account_id         = "123456789012"
    enable_mtls_validation_services = true
    mtls_validation_services        = ["auth", "ai", "realtime-stt", "core"]
    internal_mtls_material_ready    = true
    cert_loader_image_digest        = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    ai_envoy_image_digest           = "sha256:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    service_image_digests = {
      auth         = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      core         = "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
      ai           = "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
      realtime-stt = "sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
    }
  }

  assert {
    condition     = output.mtls_validation_enabled_services == toset(["auth", "ai", "realtime-stt", "core"])
    error_message = "Private validation must start only the explicit non-BFF allowlist."
  }

  assert {
    condition     = output.runtime_services_enabled == false && length(output.runtime_enabled_services) == 0
    error_message = "Private validation must keep the normal runtime gate closed."
  }

  assert {
    condition = alltrue([
      for service, container_names in output.task_definition_container_names :
      join(",", container_names) == (
        service == "ai"
        ? "cert-loader,envoy,ai"
        : "cert-loader,${service}"
      )
    ])
    error_message = "mTLS-enabled task definitions must wire cert-loader first and the AI Envoy sidecar before the application."
  }
}
