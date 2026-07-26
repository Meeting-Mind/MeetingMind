variable "name_prefix" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "security_group_id" {
  type = string
}

variable "enable_http_smoke_listener" {
  type    = bool
  default = false
}

variable "enable_stt_smoke_route" {
  description = "Enables the public STT websocket rule only after the full release gate."
  type        = bool
  default     = false
}
