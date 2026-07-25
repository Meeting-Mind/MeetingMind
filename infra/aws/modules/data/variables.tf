variable "name_prefix" {
  type = string
}

variable "data_subnet_ids" {
  type = list(string)
}

variable "database_security_group_id" {
  type = string
}

variable "cache_security_group_id" {
  type = string
}

variable "data_kms_key_arn" {
  type = string
}

variable "rds_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "rds_allocated_storage" {
  type    = number
  default = 20
}

variable "rds_max_allocated_storage" {
  type    = number
  default = 100
}

variable "valkey_node_type" {
  type    = string
  default = "cache.t4g.micro"
}
