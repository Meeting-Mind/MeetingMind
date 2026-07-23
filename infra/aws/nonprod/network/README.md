# MeetingMind NonProd Network

이 디렉터리는 MeetingMind NonProd의 첫 AWS 네트워크 기준선이다.

이 디렉터리는 서울 리전(`ap-northeast-2`)의 VPC, subnet, route table, internet gateway 초기 기준선을 정의한다. 이 네트워크는 현재 목표인 ECS Fargate 단일 리전 Multi-AZ 아키텍처에서 그대로 재사용한다.

## Design Summary

| Item | Value |
| --- | --- |
| Environment | `nonprod` |
| Region | `ap-northeast-2` |
| VPC CIDR | `10.20.0.0/16` |
| AZ count | 2 |
| Public subnets | `10.20.0.0/24`, `10.20.1.0/24` |
| Private app subnets | `10.20.16.0/20`, `10.20.32.0/20` |
| Data subnets | `10.20.48.0/24`, `10.20.49.0/24` |
| NAT Gateway | 초기 Terraform 범위 밖에서 수작업 구성됨 |
| S3 Gateway Endpoint | 초기 Terraform 범위 밖에서 수작업 구성됨 |
| Internet access | Public subnet은 IGW, private app subnet은 NAT Gateway, data subnet은 internet route 없음 |

## Tier Rules

- Public subnet: public ALB를 배치한다. Fargate application workload는 배치하지 않는다.
- Private subnet: BFF/Auth/Core/AI Fargate Task를 2개 AZ에 배치하고 기존 NAT Gateway/private route로 필요한 outbound를 제공한다.
- Data subnet: future RDS PostgreSQL and Redis/Valkey. No internet route.

## ECS Fargate Reuse

- NonProd는 전용 단일 ECS Fargate cluster를 사용한다.
- Public ALB만 public subnet에 두고 BFF/Auth/Core/AI Task는 private app subnet에 둔다.
- 내부 Security Group 허용 포트는 ALB→BFF `8081`, BFF→Auth `8082`, BFF→Core `8080`, Core→AI `8000`이다.
- 현재 Terraform은 초기 VPC/subnet/route table/IGW 구성을 보존한다. NAT Gateway/private route/S3 Gateway Endpoint와 ECS/ALB/IAM/Security Group/CloudWatch는 후속 Terraform 모듈화 범위다.

## Apply Guardrail

Do not run `terraform apply` until the AWS account and profile are confirmed. This module creates real AWS network resources when applied.

Scope notes:

- VPC, subnet, route table, and internet gateway are the right starting point because they establish network structure without committing to the runtime platform.
- NAT Gateway, ECS, RDS, ElastiCache, EC2, and VPC endpoints were not part of the initial VPC Terraform baseline.
- Current manual resources and the remaining ECS deployment work are tracked in `infra/aws/foundation-status.md` and `specs/002-bff-auth-msa/tasks.md`.

Suggested local validation:

```sh
terraform fmt -check -recursive infra/aws/nonprod/network
terraform -chdir=infra/aws/nonprod/network validate
```

`terraform validate` requires provider initialization. If the AWS provider has not been downloaded yet, run `terraform -chdir=infra/aws/nonprod/network init` only after confirming network access and provider cache behavior.
