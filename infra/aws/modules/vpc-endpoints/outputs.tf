output "endpoint_ids" {
  value = merge(
    { s3 = aws_vpc_endpoint.s3.id },
    { for name, endpoint in aws_vpc_endpoint.interface : name => endpoint.id },
  )
}
