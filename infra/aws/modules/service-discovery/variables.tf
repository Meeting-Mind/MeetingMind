variable "name_prefix" {
  type = string
}

variable "namespace_name" {
  type = string

  validation {
    condition     = var.namespace_name == "meetingmind.internal"
    error_message = "MeetingMind private service discovery must use meetingmind.internal."
  }
}

variable "vpc_id" {
  type = string
}

variable "services" {
  description = "Map of ECS service keys to private DNS service labels."
  type        = map(string)

  validation {
    condition = length(var.services) == 4 && try(
      var.services.auth == "auth"
      && var.services.core == "core"
      && var.services.ai == "ai"
      && var.services["realtime-stt"] == "stt",
      false,
    )
    error_message = "Service discovery must expose exactly auth, core, ai, and stt."
  }
}

variable "dns_ttl_seconds" {
  type    = number
  default = 10

  validation {
    condition     = var.dns_ttl_seconds >= 1 && var.dns_ttl_seconds <= 60
    error_message = "Private service discovery TTL must be between 1 and 60 seconds."
  }
}
