locals {
  # The hosted zone outlives any single environment, so it is tagged shared rather than
  # nonprod-v2.
  common_tags = {
    Project     = "MeetingMind"
    Environment = "shared"
    ManagedBy   = "terraform"
    Repository  = "Meeting-Mind/MeetingMind"
  }

  app_fqdn = "${var.app_subdomain}.${var.root_domain}"
}
