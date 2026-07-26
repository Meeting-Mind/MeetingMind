output "vpc_id" {
  value = module.network.vpc_id
}

output "regional_nat_gateway_id" {
  value = module.network.regional_nat_gateway_id
}

output "public_subnet_ids" {
  value = module.network.public_subnet_ids
}

output "private_subnet_ids" {
  value = module.network.private_subnet_ids
}

output "data_subnet_ids" {
  value = module.network.data_subnet_ids
}

output "ecr_repository_urls" {
  value = module.ecr.repository_urls
}

output "ecs_cluster_name" {
  value = module.ecs_cluster.name
}

output "service_discovery_namespace_id" {
  value = module.service_discovery.namespace_id
}

output "service_discovery_fqdns" {
  value = module.service_discovery.service_fqdns
}

output "alb_dns_name" {
  value = module.alb.dns_name
}

output "rds_address" {
  value = module.data.rds_address
}

output "rds_master_secret_arn" {
  value     = module.data.rds_master_secret_arn
  sensitive = true
}

output "valkey_primary_endpoint" {
  value = module.data.valkey_primary_endpoint
}

output "application_secret_names" {
  value = module.secrets.secret_names
}

output "task_definition_arns" {
  value = { for name, task in module.task_definition : name => task.arn }
}

output "task_definition_container_names" {
  value = { for name, task in module.task_definition : name => task.container_names }
}

output "execution_role_arns" {
  value = module.iam.execution_role_arns
}

output "github_deploy_role_arn" {
  value = module.iam.github_deploy_role_arn
}

output "runtime_services_enabled" {
  value = var.enable_runtime_services
}

output "runtime_enabled_services" {
  value = local.runtime_enabled_services
}

output "deployment_smoke_enabled" {
  value = var.enable_deployment_smoke
}

output "frontend_bucket_id" {
  value = try(module.frontend_edge[0].bucket_id, null)
}

output "frontend_distribution_id" {
  value = try(module.frontend_edge[0].distribution_id, null)
}

output "frontend_distribution_domain_name" {
  value = try(module.frontend_edge[0].distribution_domain_name, null)
}

output "service_target_group_attachment_counts" {
  value = { for service, target_groups in local.service_target_groups : service => length(target_groups) }
}

output "stt_public_smoke_route_enabled" {
  value = var.enable_http_smoke_listener && var.release_gates_acknowledged
}

output "mtls_validation_services_enabled" {
  value = var.enable_mtls_validation_services
}

output "mtls_validation_enabled_services" {
  value = local.validation_enabled_services
}
