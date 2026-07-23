# ADR 002: ECS Fargate Deployment Platform

| Field | Value |
| --- | --- |
| Status | Accepted |
| Date | 2026-07-23 |
| Scope | MeetingMind NonProd container deployment |
| Supersedes | EKS 선택을 담은 `research.md` D-007과 `clarify.md` D-008 |

## Context

MeetingMind는 `ap-northeast-2` 단일 리전 Multi-AZ에서 BFF/Auth/Core/AI를 독립 배포하고 서비스 장애와 AWS 권한을 격리해야 한다. 기존 계획은 EKS였지만 현재 팀과 워크로드에는 Kubernetes control plane, node 전략, upgrade와 Kubernetes 운영 체계가 추가 복잡도를 만든다.

기존 NonProd VPC의 public/private/data subnet, route table, NAT Gateway와 S3 Gateway Endpoint는 컨테이너 오케스트레이터와 독립적인 네트워크 경계이므로 재사용할 수 있다. 현재 VPC Terraform도 ECS Fargate와 충돌하지 않는다.

## Decision

- NonProd는 전용 단일 ECS Fargate 클러스터를 사용한다.
- BFF/Auth/Core/AI는 각각 별도의 ECS Service, Task Definition, Task Role, Security Group과 CloudWatch Log Group을 사용한다.
- `realtime-stt`는 독립 ECR repository와 서비스 경계를 유지하지만 이번 NonProd 배포에서는 Task Definition과 ECS Service 생성을 보류한다.
- 공통 ECS Task Execution Role은 ECR image pull과 CloudWatch Logs 전송 등 task 실행 권한만 가진다. 애플리케이션의 KMS/Secrets/데이터 접근은 서비스별 Task Role에 둔다.
- Fargate Task는 2개 AZ의 private app subnet에 배치하고 public ALB는 public subnet에 배치한다. private outbound는 기존 NAT Gateway를 사용한다.
- Frontend는 S3 + CloudFront + WAF로 제공한다. API 요청 경로는 사용자 → Route 53 → CloudFront/WAF → ALB → BFF다.
- Security Group에서 허용할 TCP 포트는 ALB→BFF `8081`, BFF→Auth `8082`, BFF→Core `8080`, Core→AI `8000`이다. ALB Target Group의 HTTP(S) protocol과 CloudFront origin 제한은 Q-024, 서비스 discovery와 mTLS/SPIFFE 제품은 Q-023에서 확정한다.
- ECR repository는 `bff`, `auth`, `core`, `ai`, `realtime-stt`로 분리한다. immutable tag와 Git commit SHA를 사용하고 NonProd는 기본 scan-on-push만 활성화한다. 비용을 고려해 Inspector enhanced ECR scanning은 비활성화한다.
- NonProd CloudWatch Log Group 보존 기간은 7일이다.
- 수작업 NonProd 리소스 태그는 `Project=meetingmind`, `Environment=nonprod`, `ManagedBy=manual`, `Service=<서비스명>`을 따른다.
- Production은 검증된 동일 설계를 재사용하되 별도 AWS 계정과 별도 리소스로 구성한다.
- 장기적으로 현재 수작업 ECS/ALB/IAM/Security Group/CloudWatch 구성을 Terraform 모듈과 환경별 변수로 전환한다. 기존 VPC/subnet/route Terraform은 보존한다.

## Consequences

- EC2 node와 Kubernetes control plane을 직접 운영하지 않으면서 ECS Service 단위의 독립 배포, scaling과 장애 격리를 얻는다.
- 서비스별 Task Role과 Security Group이 최소 권한과 포트 경계를 명시적으로 만든다.
- Kubernetes 리소스 생태계와 이식성, 세밀한 scheduling/오케스트레이션 기능은 줄어든다.
- Fargate CPU/memory 조합, ECS deployment 방식, Service Auto Scaling과 ALB health check를 서비스별로 검증해야 한다.
- NAT Gateway 개수와 AZ별 egress 장애 경계, CloudFront를 우회한 ALB 직접 접근 차단, 내부 service discovery, mTLS/SPIFFE 제품, 공용 리소스의 `Service` tag 값은 후속 결정이 필요하다.

## Rejected Alternative

EKS는 Kubernetes 표준성과 세밀한 운영 기능을 제공하지만 현재 서비스 수와 팀 규모에 비해 control plane, node, upgrade, ingress와 Kubernetes 보안 정책 운영 부담이 크다. 현재 요구는 ECS Fargate의 Service/Task Definition, ALB, IAM Task Role과 Security Group으로 충족할 수 있으므로 선택하지 않는다.

## Verification

- `spec.md`, `research.md`, `plan.md`, `tasks.md`, `clarify.md`의 현재/미래 배포 계획이 ECS Fargate를 기준으로 일치한다.
- 기존 VPC Terraform 리소스는 변경하지 않고 ECS Fargate와 재사용 가능함을 유지한다.
- EKS 문자열은 대체된 결정이나 과거 구현 기록처럼 역사적 맥락에서만 남긴다.
