output "vpc_id" {
  value = aws_vpc.this.id
}

output "vpc_cidr" {
  value = aws_vpc.this.cidr_block
}

output "availability_zones" {
  value = var.availability_zones
}

output "public_subnet_ids" {
  value = [for key in sort(keys(aws_subnet.public)) : aws_subnet.public[key].id]
}

output "private_subnet_ids" {
  value = [for key in sort(keys(aws_subnet.private)) : aws_subnet.private[key].id]
}

output "data_subnet_ids" {
  value = [for key in sort(keys(aws_subnet.data)) : aws_subnet.data[key].id]
}

output "private_route_table_ids" {
  value = [for key in sort(keys(aws_route_table.private)) : aws_route_table.private[key].id]
}

output "data_route_table_ids" {
  value = [for key in sort(keys(aws_route_table.data)) : aws_route_table.data[key].id]
}

output "regional_nat_gateway_id" {
  value = aws_nat_gateway.this.id
}
