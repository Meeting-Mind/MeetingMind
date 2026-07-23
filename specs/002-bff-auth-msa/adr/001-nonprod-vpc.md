# ADR 001: NonProd VPC Baseline

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-07-21 |
| Scope | MeetingMind NonProd AWS network |

## Context

MeetingMind의 장기 목표는 AWS 단일 리전 Multi-AZ에서 BFF/Auth/Core/AI를 독립 배포하는 것이다. 현재 단계는 그 목표를 바꾸지 않고, 실제 배포 인프라의 첫 경계인 NonProd VPC와 subnet 기준선을 고정한다.

현재 단계에서는 AWS 액세스 포털의 `MeetingMind-NonProd` 계정에서 `AWSAdministratorAccess`로 접속하고, 서울 리전(`ap-northeast-2`)을 기준으로 NonProd 네트워크 설계부터 시작한다.

## Decision

- NonProd 첫 리전은 `ap-northeast-2`로 고정한다.
- VPC는 `10.20.0.0/16`을 사용한다.
- 2개 AZ에 Public, Private, Data subnet을 각각 둔다.
- Private app subnet은 future ECS Fargate task IP 여유를 위해 `/20`으로 크게 잡는다.
- Data subnet은 RDS PostgreSQL, Redis/Valkey용 격리 subnet으로 두고 internet route를 만들지 않는다.
- 초기 Terraform은 VPC, subnets, internet gateway, route tables만 만든다.
- NAT Gateway, ECS Fargate, RDS, ElastiCache, interface VPC endpoint는 이 초기 VPC baseline 단계에서 만들지 않고 각 후속 task에서 만든다.

## Network Plan

| Tier | AZ 1 CIDR | AZ 2 CIDR | Route |
| --- | --- | --- | --- |
| Public | `10.20.0.0/24` | `10.20.1.0/24` | `0.0.0.0/0 -> IGW` |
| Private app | `10.20.16.0/20` | `10.20.32.0/20` | Local only initially |
| Data | `10.20.48.0/24` | `10.20.49.0/24` | Local only |

## Consequences

- 초기 VPC 자체는 이후 목표 ECS Fargate baseline에서 재사용한다.
- 이 ADR의 초기 baseline 이후 NonProd에는 NAT Gateway, private route와 S3 Gateway Endpoint가 수작업으로 추가됐다. 현재 Terraform은 초기 네트워크 기준선으로 보존하고 후속 모듈화에서 실제 상태를 수렴한다.
- ECR pull, CloudWatch Logs와 Secrets Manager 접근은 기존 NAT Gateway를 사용한다. endpoint 추가와 NAT Gateway AZ 이중화 여부는 후속 ECS foundation 검증에서 결정한다.
- Production은 NonProd 검증 뒤 CIDR만 충돌 없이 바꿔 같은 구조를 반복한다.

## Verification

- Terraform formatting: `terraform fmt -check -recursive infra/aws/nonprod/network`
- Terraform provider validation: `terraform -chdir=infra/aws/nonprod/network validate`
- AWS console validation after apply: VPC CIDR, 6개 subnet, public route table의 IGW default route, private/data route table의 local-only 상태 확인
