terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

locals {
  tls_enabled     = var.tls_bundle != null
  envoy_enabled   = var.envoy_sidecar != null
  tls_volume_name = "meetingmind-tls"
  tls_mount_path  = "/run/meetingmind/tls"

  port_mappings = [{
    name          = "http"
    containerPort = var.container_port
    hostPort      = var.container_port
    protocol      = "tcp"
    appProtocol   = "http"
  }]

  base_container = {
    name      = var.container_name
    image     = var.image
    essential = true

    environment = [
      for name in sort(keys(var.environment)) : {
        name  = name
        value = var.environment[name]
      }
    ]

    secrets = [
      for name in sort(keys(var.secrets)) : {
        name      = name
        valueFrom = var.secrets[name]
      }
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = var.log_group_name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = var.container_name
      }
    }

    linuxParameters = {
      initProcessEnabled = true
    }

    readonlyRootFilesystem = false
  }

  container = merge(concat(
    [local.base_container],
    local.envoy_enabled ? [] : [{ portMappings = local.port_mappings }],
    length(var.container_command) == 0 ? [] : [{ command = var.container_command }],
    length(var.health_check_command) == 0 ? [] : [{
      healthCheck = {
        command     = var.health_check_command
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }
    }],
    !local.tls_enabled ? [] : [{
      user = "10001:10001"

      mountPoints = [{
        sourceVolume  = local.tls_volume_name
        containerPath = local.tls_mount_path
        readOnly      = true
      }]

      dependsOn = [{
        containerName = "cert-loader"
        condition     = "SUCCESS"
      }]
    }],
  )...)

  cert_loader_container = !local.tls_enabled ? null : {
    name      = "cert-loader"
    image     = var.tls_bundle.loader_image
    essential = false
    user      = "0:0"

    command = concat(
      [
        "--secret-arn", var.tls_bundle.secret_arn,
        "--version-stage", var.tls_bundle.version_stage,
        "--expected-service", var.tls_bundle.expected_service,
        "--expected-spiffe-id", var.tls_bundle.expected_spiffe_id,
      ],
      flatten([for dns_san in var.tls_bundle.expected_dns_sans : ["--expected-dns-san", dns_san]]),
      flatten([for eku in var.tls_bundle.expected_ekus : ["--expected-eku", eku]]),
      ["--output-dir", local.tls_mount_path],
    )

    environment = [{
      name  = "AWS_REGION"
      value = var.aws_region
    }]

    mountPoints = [{
      sourceVolume  = local.tls_volume_name
      containerPath = local.tls_mount_path
      readOnly      = false
    }]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = var.log_group_name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "cert-loader"
      }
    }

    readonlyRootFilesystem = true
  }

  envoy_container = !local.envoy_enabled ? null : {
    name      = "envoy"
    image     = var.envoy_sidecar.image
    essential = true
    user      = "10001:10001"

    portMappings = local.port_mappings

    mountPoints = [{
      sourceVolume  = local.tls_volume_name
      containerPath = local.tls_mount_path
      readOnly      = true
    }]

    dependsOn = [{
      containerName = "cert-loader"
      condition     = "SUCCESS"
    }]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = var.log_group_name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "envoy"
      }
    }

    readonlyRootFilesystem = true
  }

  worker_containers = [
    for worker_name in sort(keys(var.background_workers)) : merge(local.base_container, {
      name    = worker_name
      command = var.background_workers[worker_name]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = var.log_group_name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = worker_name
        }
      }
    })
  ]

  containers = concat(
    local.tls_enabled ? [local.cert_loader_container] : [],
    local.envoy_enabled ? [local.envoy_container] : [],
    [local.container],
    local.worker_containers,
  )
}

resource "aws_ecs_task_definition" "this" {
  family                   = var.family
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  skip_destroy             = true
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = var.execution_role_arn
  task_role_arn            = var.task_role_arn
  container_definitions    = jsonencode(local.containers)

  runtime_platform {
    cpu_architecture        = "ARM64"
    operating_system_family = "LINUX"
  }

  dynamic "volume" {
    for_each = local.tls_enabled ? [local.tls_volume_name] : []

    content {
      name = volume.value
    }
  }

  ephemeral_storage {
    size_in_gib = var.ephemeral_storage_gib
  }

  tags = {
    Service = var.container_name
  }
}
