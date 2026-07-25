# MeetingMind NonProd V2

이 root는 기존 수작업 NonProd를 import하거나 참조하지 않고 새 `nonprod-v2`를 만든다. 기본값은 ECS service를 생성하지 않으며 VPC, data, ECR, IAM, ALB, Cloud Map namespace/service와 task definition까지 안전하게 준비한다.

## 1. State bootstrap

```bash
export AWS_PROFILE=meetingmind-nonprod
cd infra/aws/bootstrap/state
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform plan -out=bootstrap.tfplan
terraform apply bootstrap.tfplan
```

`terraform.tfvars`에서 account ID와 전역 고유 bucket 이름을 실제 값으로 바꾼다. 출력된 값을 `backend.hcl`에 옮긴다. bootstrap local state, `backend.hcl`, saved plan은 커밋하지 않는다.

## 2. Foundation plan

```bash
export AWS_PROFILE=meetingmind-nonprod
cd infra/aws/environments/nonprod-v2
cp backend.hcl.example backend.hcl
cp terraform.tfvars.example terraform.tfvars
terraform init -backend-config=backend.hcl
terraform fmt -check -recursive
terraform validate
terraform plan -out=foundation.tfplan
```

첫 foundation plan은 다음 값으로 유지한다.

```hcl
enable_http_smoke_listener      = false
enable_runtime_services         = false
runtime_enabled_services        = []
runtime_gates_acknowledged      = false
internal_mtls_material_ready    = false
internal_mtls_runtime_verified  = false
enable_mtls_validation_services = false
mtls_validation_services        = []
enable_autoscaling              = false
image_tag                       = "bootstrap"
service_image_digests           = {}
cert_loader_image_digest        = null
```

Plan에서 기존 수작업 resource ID가 보이거나 delete가 한 건이라도 보이면 apply를 중단한다.

## 3. Foundation 범위

- S3 backend bootstrap과 native lockfile
- `10.20.0.0/16` VPC와 2개 AZ Public/Private/Data subnet
- IGW, Regional NAT Gateway automatic mode, route table, 기본 NACL, VPC Flow Logs
- S3/ECR/Logs/Secrets Manager/KMS VPC endpoint
- 서비스별 Security Group
- application/data/log/JWT signing KMS key
- ECR 5개와 lifecycle
- 빈 PostgreSQL 16 RDS와 RDS-managed master secret
- Valkey 7.2 primary+replica, Multi-AZ, IAM authentication user
- application/provider Secrets Manager container
- ECS execution/task role, 선택적 GitHub OIDC deployment role
- public ALB와 BFF/STT target group
- Cloud Map Private DNS `meetingmind.internal`과 `auth/core/ai/stt` service
- ECS cluster, task definition, CloudWatch logs/dashboard/alarms

GitHub OIDC를 활성화할 때는 repository 전체 wildcard가 아니라 보호된 branch 또는 environment subject를 명시한다.

```hcl
enable_github_oidc   = true
github_oidc_subjects = ["repo:Meeting-Mind/MeetingMind:ref:refs/heads/main"]
```

## 4. Console secret 입력

Terraform은 secret version을 만들지 않는다. Foundation apply 뒤 Secrets Manager Console에서 다음 secret에 값을 입력한다.

| Secret suffix | 값 |
| --- | --- |
| `auth/db-runtime-password` | `meetingmind_auth_app` role password |
| `auth/db-migration-password` | `meetingmind_auth_migrator` role password |
| `auth/refresh-hash` | 최소 32자 random secret |
| `auth/signing-key-ring` | KMS signing key ARN을 포함한 Auth key-ring JSON |
| `core/db-runtime-password` | `meetingmind_core_app` role password |
| `core/livekit-url` | LiveKit Cloud `wss://` URL |
| `core/livekit-api-key` | LiveKit API key |
| `core/livekit-api-secret` | LiveKit API secret |
| `ai/database-url` | password를 포함한 AI PostgreSQL DSN |
| `ai/openai-api-key` | OpenAI API key |
| `stt/db-runtime-password` | `meetingmind_stt_app` role password |
| `stt/egress-ws-secret` | 최소 32자 random HMAC secret |
| `stt/soniox-api-key` | Soniox API key |
| `stt/openai-api-key` | STT fallback OpenAI API key |
| `stt/debug-token` | debug endpoint용 random token |
| `bff/tls-bundle` | `scripts/pki/nonprod` bundle 명령이 만든 BFF TLS bundle JSON |
| `auth/tls-bundle` | 같은 방식의 Auth TLS bundle JSON |
| `core/tls-bundle` | 같은 방식의 Core TLS bundle JSON |
| `ai/tls-bundle` | 같은 방식의 AI TLS bundle JSON |
| `stt/tls-bundle` | 같은 방식의 Realtime STT TLS bundle JSON |

TLS bundle 값 입력은 offline CA 저장 위치 확인과 별도 사용자 승인이 있는 Phase 8에서만 수행한다. 각 TLS bundle secret은 resource policy로 해당 서비스 task role 외의 `GetSecretValue`를 명시적으로 거부하므로, 입력 후 Console로 값을 다시 조회할 수 없는 것이 정상이다.

RDS master password는 RDS가 생성·회전한다. Valkey는 장기 password를 사용하지 않는다. 값을 `.tfvars`, plan, shell argument 또는 Terraform output에 넣지 않는다.

## 5. Database bootstrap gate

기존 RDS 데이터는 가져오지 않는다. RDS-managed master secret으로 접속해 다음 작업을 별도 검증된 bootstrap 절차로 수행한다.

1. `meetingmind`, `meetingmind_auth`, `meetingmind_stt` database 확인/생성
2. 서비스별 migration/runtime role 생성
3. Console에 입력한 password와 DB role password 일치
4. Core/Auth/STT forward-only Flyway migration 실행
5. runtime role의 DDL/다른 서비스 database 접근 거부 확인

Terraform은 PostgreSQL 내부 database/role/schema를 관리하지 않는다. Provider state에 DB password를 넣지 않기 위한 경계다.

## 6. Image gate

ECR image는 ARM64 Fargate와 일치하게 빌드하고 Git commit SHA를 immutable tag로 사용한다.

```bash
docker buildx build --platform linux/arm64 --tag <repository>:<git-sha> --push <service-directory>
```

`bff`, `auth`, `core`, `ai`, `realtime-stt` 다섯 image digest와 commit SHA 대응을 기록한다. runtime Task Definition은 tag가 아니라 child manifest digest를 사용한다.

```hcl
service_image_digests = {
  bff          = "sha256:<digest>"
  auth         = "sha256:<digest>"
  core         = "sha256:<digest>"
  ai           = "sha256:<digest>"
  realtime-stt = "sha256:<digest>"
}
```

## 7. Runtime gate

다음 항목을 모두 완료하기 전에는 `enable_runtime_services=false`를 유지한다.

- Q-013 SLO/RTO/RPO와 autoscaling 값 승인
- Cloud Map 주소와 mTLS/SPIFFE certificate delivery 구현
- 필수 secret version 입력
- database/bootstrap migration 완료
- 5개 application과 cert-loader/Envoy를 포함한 7개 ECR image digest·scan 통과
- BFF가 Valkey IAM의 15분 token 갱신과 12시간 connection 재인증을 지원하고 TLS 연결 테스트 통과
- Auth signing key-ring JSON의 5분 선게시/active 시각 검증
- 내부 서비스 주소와 workload identity negative test 통과

mTLS gate는 순환 의존성을 없애기 위해 두 단계로 분리되어 있다.

1. `internal_mtls_material_ready=true`: TLS bundle `AWSCURRENT` version, 7개 image digest/scan, loader/Envoy config와 로컬 mTLS handshake 증거가 완료된 상태다. 이 값은 private validation mode만 연다.
2. `internal_mtls_runtime_verified=true`: private validation deployment에서 AWS positive/negative matrix와 rotation/rollback drill 증거가 완료된 상태다. 이 값이 있어야 정상 runtime을 켤 수 있다.

Private validation은 public BFF/ALB traffic 없이 수행한다.

```hcl
enable_mtls_validation_services = true
mtls_validation_services        = ["auth"] # Auth→AI/STT→Core 순서로 확장
internal_mtls_material_ready    = true
cert_loader_image_digest        = "sha256:<digest>"
```

validation mode는 BFF를 허용하지 않고, HTTP smoke listener와 동시에 켤 수 없으며, `enable_runtime_services`와 상호 배타다. 검증 완료 뒤에만 validation mode를 끄고 다음 값을 함께 변경한다.

```hcl
enable_runtime_services         = true
runtime_enabled_services        = ["auth"]
runtime_gates_acknowledged      = true
internal_mtls_runtime_verified  = true
enable_mtls_validation_services = false
mtls_validation_services        = []
```

`runtime_enabled_services`는 Auth→AI/STT→Core→BFF 순서의 staged allowlist다. 각 단계의 runtime 검증이 끝날 때만 다음 서비스를 추가한다. `internal_mtls_runtime_verified`는 T047-D/T048-V의 인증서·principal 증거가 생기기 전에는 `false`로 유지한다. `runtime_gates_acknowledged`는 기술적 검증을 대신하지 않는 명시적 안전장치다. 상세 전환 계획은 `../../nonprod-v2/mtls-implementation-plan.md`를 따른다.

validation 또는 runtime mode가 켜지면 모든 task definition에 cert-loader init container, task-scoped `meetingmind-tls` volume과 application read-only mount가 추가된다. loader는 task role로 자기 서비스의 TLS bundle secret만 읽고, 검증 실패 시 `SUCCESS` dependency가 application 시작을 차단한다.

## 8. HTTP smoke gate

도메인 발급 전 ALB HTTP listener는 health/API 인프라 확인용이다.

```hcl
allowed_ingress_cidrs      = ["<operator-public-ip>/32"]
enable_http_smoke_listener = true
```

`0.0.0.0/0`는 validation에서 거부한다. HTTP에서는 `Secure`/`__Host-` cookie와 LiveKit WSS를 검증하지 않는다. smoke 종료 후 listener를 다시 비활성화한다.

## 9. 아직 이 root가 만들지 않는 것

- Route 53, ACM, HTTPS listener, CloudFront/WAF
- CloudFront origin 검증과 direct ALB 우회 차단
- offline NonProd CA material, TLS bundle secret version과 실제 인증서 발급 (cert-loader delivery/IAM/volume source는 T047-B3에서 구현됨)
- AI Envoy sidecar와 rotation runbook의 실제 구현
- Production account/state
- 기존 수작업 환경 삭제

도메인과 Q-023 결정이 준비되면 별도 검토된 변경으로 추가한다. 기존 환경 삭제는 V2 cutover 및 rollback 관측이 끝난 뒤 별도 승인으로 수행한다.
