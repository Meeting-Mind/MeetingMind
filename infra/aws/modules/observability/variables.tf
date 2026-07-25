variable "name_prefix" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "services" {
  type = set(string)
}

variable "logs_kms_key_arn" {
  type = string
}

variable "log_retention_days" {
  type    = number
  default = 7
}

variable "alarm_email" {
  type     = string
  default  = null
  nullable = true
}

variable "ecs_cluster_name" {
  type = string
}

variable "alb_arn_suffix" {
  type = string
}

variable "target_group_arn_suffixes" {
  type = map(string)
}

variable "rds_identifier" {
  type = string
}

variable "valkey_replication_group_id" {
  type = string
}

variable "nat_gateway_id" {
  type = string
}
