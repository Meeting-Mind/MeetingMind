provider "aws" {
  region = var.aws_region

  allowed_account_ids = [var.expected_aws_account_id]

  default_tags {
    tags = local.common_tags
  }
}

# CloudFront only accepts viewer certificates from us-east-1, regardless of where the
# distribution serves from.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  allowed_account_ids = [var.expected_aws_account_id]

  default_tags {
    tags = local.common_tags
  }
}
