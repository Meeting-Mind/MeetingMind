output "vpc_id" {
  description = "MeetingMind NonProd VPC ID."
  value       = aws_vpc.this.id
}

output "vpc_cidr" {
  description = "MeetingMind NonProd VPC CIDR block."
  value       = aws_vpc.this.cidr_block
}

output "availability_zones" {
  description = "Availability zones selected for this network."
  value       = local.selected_azs
}

output "public_subnet_ids" {
  description = "Public subnet IDs."
  value       = { for name, subnet in aws_subnet.public : name => subnet.id }
}

output "private_subnet_ids" {
  description = "Private application subnet IDs."
  value       = { for name, subnet in aws_subnet.private : name => subnet.id }
}

output "data_subnet_ids" {
  description = "Data subnet IDs."
  value       = { for name, subnet in aws_subnet.data : name => subnet.id }
}
