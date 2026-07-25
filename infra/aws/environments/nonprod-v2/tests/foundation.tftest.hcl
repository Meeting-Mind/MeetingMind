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
  }

  expect_failures = [terraform_data.smoke_gate]
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
    error_message = "Only explicitly allowlisted services may be runtime enabled."
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
