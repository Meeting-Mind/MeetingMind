# NonProd V2 mTLS, SPIFFE, Certificate Delivery Implementation Plan

## 1. 결정 기준선

2026-07-25 사용자 결정은 다음과 같다.

- AI TLS 종료: AI Task 안의 Envoy sidecar
- CA: AWS Private CA를 사용하지 않는 암호화된 offline NonProd CA
- 인증서 전달: 전용 최소 ARM64 `meetingmind-cert-loader`
- 인증서 저장: 서비스별 AWS Secrets Manager TLS bundle
- leaf 인증서 갱신: 새 secret version 검증 후 `AWSCURRENT` 승격과 ECS force deployment
- Production: AWS Private CA 또는 자동화 CA를 별도 승인하기 전 NonProd CA를 재사용하지 않음

이 결정은 CA 구독 비용을 만들지 않지만 Secrets Manager, ECR, KMS API와 Fargate task resource 비용까지 0으로 만들지는 않는다.

## 2. 범위와 비범위

### 범위

- BFF/Auth/Core/AI/Realtime STT용 공통 NonProd trust domain과 서비스별 leaf 인증서
- secret 원문을 Terraform state, task definition, 환경변수와 로그에 남기지 않는 bundle delivery
- Java 서비스의 기존 `/run/meetingmind/tls` PEM 계약을 ECS shared task volume에 연결
- AI Envoy의 downstream mTLS, SPIFFE principal allowlist와 loopback Uvicorn upstream
- Core→AI shared token 제거
- leaf rotation, CA overlap rotation과 rollback runbook
- source test와 실제 AWS positive/negative runtime evidence

### 비범위

- Production CA 선택과 Production 인증서 발급
- SPIRE server/agent 또는 서비스 메시 도입
- public edge Route 53/ACM/CloudFront/WAF
- Terraform이 CA/private key/leaf secret version을 생성하는 구성
- 기존 수작업 NonProd 인증서나 리소스의 재사용·삭제

## 3. Trust와 identity 계약

### 3.1 Trust domain

- trust domain: `meetingmind.internal`
- namespace: `nonprod-v2`
- SPIFFE ID 형식: `spiffe://meetingmind.internal/ns/nonprod-v2/sa/{serviceAccount}`

| Service | Service account | DNS SAN | EKU |
| --- | --- | --- | --- |
| BFF | `meetingmind-bff` | 없음 | `clientAuth` |
| Auth | `meetingmind-auth` | `auth.meetingmind.internal` | `serverAuth` |
| Core | `meetingmind-core` | `core.meetingmind.internal` | `clientAuth`, `serverAuth` |
| AI | `meetingmind-ai` | `ai.meetingmind.internal` | `serverAuth` |
| Realtime STT | `meetingmind-realtime-stt` | `stt.meetingmind.internal` | `serverAuth` |

한 인증서에는 위 표의 정확히 하나인 SPIFFE URI SAN만 허용한다. 서버 인증서는 exact Cloud Map DNS SAN을 추가한다. wildcard DNS, IP SAN, 추가 URI SAN과 임의 EKU는 허용하지 않는다.

### 3.2 Caller allowlist

| Destination | Path boundary | Allowed SPIFFE caller |
| --- | --- | --- |
| Auth | internal auth, JWKS | `meetingmind-bff`, `meetingmind-core` 중 endpoint별 기존 allowlist |
| Core | internal projection와 BFF proxy 대상 | `meetingmind-bff` |
| AI | `/api/internal/**` | `meetingmind-core` |
| Realtime STT | Core 업무 API | `meetingmind-core` |

Realtime STT의 LiveKit Egress WSS 경로는 mTLS workload 경계가 아니라 기존 1회용 token과 ALB 경계를 유지한다.

## 4. Offline NonProd CA

### 4.1 Key custody

- repository에는 OpenSSL 설정, 검증 스크립트와 runbook만 저장한다.
- root/intermediate private key, serial database, 발급 결과와 passphrase는 repository 밖의 사용자가 지정한 암호화 저장 위치에만 둔다.
- PKI 스크립트는 출력 경로를 명시적으로 요구하고 repository 내부, symlink, world-readable 경로를 거부한다.
- Terraform, `.tfvars`, saved plan, shell argument, CI artifact와 CloudWatch log에 CA 또는 leaf private key를 넣지 않는다.
- Codex가 실제 CA/leaf key를 생성하거나 AWS secret version을 쓰는 단계는 사용자의 별도 실행 승인과 저장 위치 확인 뒤에만 수행한다.

### 4.2 초기 수명 기본값

- root CA: 5년
- intermediate CA: 1년
- leaf: 90일
- leaf rotation 시작: 만료 30일 전

알고리즘은 먼저 ECDSA P-256으로 Java 21, Envoy와 OpenSSL 상호운용 테스트를 수행한다. 호환성 실패 증거가 있을 때만 RSA-3072로 변경하며 결과를 `implement.md`에 기록한다.

### 4.3 발급 도구

`scripts/pki/nonprod/`에 다음의 fail-closed 도구를 둔다.

- root/intermediate 초기화
- service manifest 기반 CSR/key 생성
- exact SPIFFE/DNS/EKU extension을 적용한 leaf 서명
- certificate chain, key match, validity, SAN/EKU 검증
- Secrets Manager에 입력할 bundle JSON 생성

CI는 임시 디렉터리의 테스트 전용 CA만 생성한다. 고정 private key와 실제 NonProd certificate는 fixture로 커밋하지 않는다.

## 5. TLS bundle과 cert-loader

### 5.1 Secret contract

Terraform은 아래 다섯 secret container와 resource policy만 만든다.

```text
/meetingmind-nonprod-v2/bff/tls-bundle
/meetingmind-nonprod-v2/auth/tls-bundle
/meetingmind-nonprod-v2/core/tls-bundle
/meetingmind-nonprod-v2/ai/tls-bundle
/meetingmind-nonprod-v2/stt/tls-bundle
```

각 secret version은 한 번에 교체되는 단일 JSON document다.

```json
{
  "schemaVersion": 1,
  "environment": "nonprod-v2",
  "service": "core",
  "spiffeId": "spiffe://meetingmind.internal/ns/nonprod-v2/sa/meetingmind-core",
  "certificatePem": "<PEM certificate>",
  "privateKeyPem": "<PEM private key>",
  "caBundlePem": "<PEM CA bundle>",
  "notBefore": "RFC3339",
  "notAfter": "RFC3339"
}
```

한 bundle/version에 certificate, private key와 trust bundle을 함께 넣어 부분 갱신을 방지한다. Terraform은 secret version과 JSON 값을 만들거나 읽지 않는다.

`caBundlePem`은 `(intermediate, root)` 순서 쌍을 정상 상태에서 1개, CA overlap rotation window에서만 2개 담는다. 각 쌍은 자체적으로 유효한 CA chain이어야 하고, leaf의 presented intermediate는 정확히 한 쌍과 일치해야 하며, 중복 intermediate/root와 홀수 개 인증서는 loader와 PKI 도구가 fail closed로 거부한다.

### 5.2 `meetingmind-cert-loader`

새 top-level `cert-loader/`는 Linux ARM64 정적 binary와 최소 runtime image를 만든다. 구현 기본안은 Go 표준 `crypto/x509`와 AWS SDK for Go v2다. 새 언어/SDK를 추가하는 이유는 공식 AWS CLI image의 지원 경계 밖인 shell/JSON/PEM 도구에 의존하지 않고, 단일 작은 binary에서 검증·원자 쓰기·오류 redaction을 구현하기 위해서다.

Loader 입력은 secret ARN, version stage, expected service/SPIFFE/DNS/EKU와 출력 디렉터리뿐이다. secret value 자체는 환경변수로 받지 않는다.

Loader는 다음 순서로 동작한다.

1. ECS task role credential로 자신의 exact TLS bundle secret을 조회한다.
2. schema와 service/environment metadata를 검증한다.
3. PEM, chain, validity, private-key match, exact URI/DNS SAN과 EKU를 검증한다.
4. `/run/meetingmind/tls/.staging-*`에 파일을 쓴다.
5. `tls.key`는 `0400`, `tls.crt`와 `ca.crt`는 `0444`로 설정하고 app UID/GID 소유로 바꾼다.
6. `fsync`와 rename으로 완성된 세 파일만 노출한다.
7. fingerprint, expiry와 version ID의 비민감 metadata만 기록하고 성공 종료한다.

조회·파싱·검증·쓰기 중 하나라도 실패하면 secret 원문 없이 종료 코드가 실패하고 application container는 시작하지 않는다.

### 5.3 ECS volume와 identity

- task-scoped ephemeral volume 이름은 `meetingmind-tls`다.
- loader만 read-write로 mount하고 application/Envoy는 read-only로 mount한다.
- loader는 init 단계에서만 root로 실행해 빈 volume ownership을 고정하고 즉시 종료한다.
- application과 Envoy는 고정 non-root UID/GID `10001:10001`로 실행한다.
- loader는 `essential=false`, application은 `dependsOn cert-loader:SUCCESS`다.
- Envoy가 있는 AI task는 `Envoy dependsOn cert-loader:SUCCESS`, AI app은 loopback listener 준비 후 Envoy health가 성공해야 ready로 본다.
- task role은 해당 서비스의 TLS bundle ARN에만 `DescribeSecret/GetSecretValue`와 exact KMS key decrypt를 허용한다. 다른 서비스 bundle은 거부한다.

## 6. AI Envoy boundary

### 6.1 Network

```text
Core HTTPS mTLS
  -> ai.meetingmind.internal:8000
  -> Envoy 0.0.0.0:8000
       - CA/client certificate verification
       - exact Core SPIFFE RBAC
       - incoming XFCC sanitize
       - verified URI only in XFCC
  -> Uvicorn 127.0.0.1:8001
```

- Uvicorn은 `127.0.0.1:8001`에만 bind하고 task ENI에 직접 노출하지 않는다.
- Envoy는 static config만 사용하고 admin endpoint는 loopback에만 bind한다.
- downstream TLS는 client certificate를 필수로 요구한다.
- Envoy RBAC가 Core의 exact SPIFFE URI SAN만 허용한다.
- `forward_client_cert_details=SANITIZE_SET`로 외부 XFCC를 제거하고 검증된 current certificate URI만 다시 만든다.
- access log에는 XFCC, certificate, Authorization, token과 secret 값을 기록하지 않는다.

### 6.2 FastAPI

- ECS는 `AI_INTERNAL_AUTH_MODE=mtls-proxy`를 설정한다.
- `mtls-proxy` mode의 `/api/internal/**`는 Envoy가 정규화한 XFCC에서 정확히 하나의 expected SPIFFE URI만 수락한다.
- missing, malformed, multiple URI, wrong principal과 일반 spoof header를 fail closed한다.
- local/on-prem PoC의 명시적 `shared-token` mode는 별도 compatibility 경계로 유지할 수 있지만 ECS task definition에는 `AI_INTERNAL_SERVICE_TOKEN`을 넣지 않는다.
- Core의 `X-MeetingMind-Service-Token` 전송과 Terraform의 `core/ai-internal-token` secret/IAM 참조를 ECS target 경계에서 제거한다.

Envoy image는 검토한 ARM64 upstream version과 digest를 전용 V2 ECR repository에 mirror하고 scan한다. `latest` tag와 public registry runtime pull은 사용하지 않는다.

## 7. Runtime gate 분리

현재 `internal_mtls_ready` 하나는 “실제 AWS 검증 전에는 false”와 “검증하려면 service가 떠야 함”이 순환한다. 다음 두 gate로 분리한다.

| Gate | 의미 | 허용 범위 |
| --- | --- | --- |
| `internal_mtls_material_ready` | image scan, secret current version, loader/Envoy config와 local handshake 완료 | private mTLS validation deployment |
| `internal_mtls_runtime_verified` | AWS positive/negative matrix와 rotation/rollback evidence 완료 | 정상 staged runtime과 최종 acknowledgement |

별도 `enable_mtls_validation_services=false`를 기본값으로 둔다. 이 mode는 exact allowlist의 private service만 만들고 BFF browser traffic과 ALB listener를 활성화하지 않는다. validation mode와 정상 runtime mode를 동시에 켤 수 없게 validation을 추가한다.

검증 순서는 Auth → AI/Realtime STT → Core다. Core까지 띄운 뒤 실제 caller/callee 검증을 수행하고, 성공해야 `internal_mtls_runtime_verified=true`와 기존 `runtime_gates_acknowledged=true`를 사용할 수 있다. BFF는 최종 staged runtime에서 마지막으로 추가한다.

`enable_runtime_services`, `runtime_gates_acknowledged`, `internal_mtls_runtime_verified`의 기본값은 계속 `false`다.

## 8. 구현 순서

| Phase | Task | 주요 파일 | 완료 기준 |
| --- | --- | --- | --- |
| 0 | 계약/결정 고정 | `clarify.md`, `plan.md`, `tasks.md`, AI internal contract | Q-029 결정, secret schema, SPIFFE/EKU/allowlist와 gate 의미 일치 |
| 1 | Offline PKI tooling | `scripts/pki/nonprod/**`, runbook, tests | 임시 CA로 5개 manifest 발급, exact SAN/EKU와 잘못된 manifest 거부 |
| 2 | cert-loader | `cert-loader/**`, CI | ARM64 build, unit/integration, HIGH/CRITICAL 0, secret/log scan 통과 |
| 3 | Terraform secret/IAM/volume | `modules/secrets`, `iam`, `ecs-task`, environment/tests | service별 bundle, task-role exact read, shared volume/dependency와 gate source test 통과 |
| 4 | AI Envoy/FastAPI | `ai/envoy/**`, `ai/app/**`, AI Docker/CI/tests | loopback-only Uvicorn, Envoy exact SPIFFE, shared token 제거와 negative test 통과 |
| 5 | Local end-to-end | local compose/script, all service tests | Core cert 성공, no-cert/wrong-CA/wrong-SPIFFE/header spoof/direct Uvicorn 거부 |
| 6 | Image supply | ECR/CI/runbook | 5 app + cert-loader + mirrored Envoy의 ARM64 digest와 scan finding 0 |
| 7 | AWS source plan | Terraform root/tests | `fmt`, `validate`, mock tests, IaC/secret scan과 no-delete plan 검토 |
| 8 | AWS material setup | 사용자 승인, Console/CLI runbook | offline key 위치 확인, 5 bundle `AWSCURRENT`, 원문 비기록 |
| 9 | Private validation deployment | validation gate, ECS | Auth→AI/STT→Core private service와 loader/Envoy health 정상 |
| 10 | Rotation drill | pending/current stages, ECS force deployment | leaf 교체, rollback, CA overlap 3단계 drill 통과 |
| 11 | Runtime security evidence | verifier task/runbook | positive와 모든 negative matrix, cross-secret IAM denial, logs redaction 통과 |
| 12 | Runtime enable | staged allowlist | runtime verified/acknowledged 후 Auth→AI/STT→Core→BFF 순차 활성화 |

Phase 0~7은 AWS resource mutation 없이 수행할 수 있다. Phase 8 이후 secret write, apply, ECS update와 force deployment는 별도 사용자 승인 뒤 실행한다.

## 9. 검증 행렬

### Source와 container

- cert-loader malformed JSON/PEM, missing field, expired/not-yet-valid certificate
- certificate/private-key mismatch
- wrong/multiple SPIFFE URI, wrong DNS, wrong EKU, untrusted chain
- unsafe output path/symlink와 partial write
- loader error/stdout/stderr에 PEM/private key/secret value 부재
- Envoy config validation
- AI FastAPI exact XFCC parser와 shared-token ECS mode 부재
- ARM64 image build와 Trivy HIGH/CRITICAL 0
- Terraform `fmt`, `validate`, mock tests, IaC scan, Gitleaks

### AWS runtime

| Case | Expected rejection layer |
| --- | --- |
| approved caller/callee | TLS와 application allowlist 성공 |
| no client certificate | Envoy/Tomcat TLS handshake |
| wrong CA | TLS handshake |
| wrong SPIFFE | Envoy RBAC 또는 application principal filter |
| multiple SPIFFE URI | loader/source test 또는 application filter |
| hostname mismatch | caller TLS hostname verification |
| spoofed XFCC/test header | TLS 또는 application |
| direct Uvicorn `8001` | task ENI network |
| wrong source SG | VPC network |
| other service TLS secret read | IAM/Secrets Manager |
| expired/invalid new bundle | loader `SUCCESS` dependency |

Evidence에는 request ID, certificate fingerprint, SPIFFE ID, TLS/HTTP/network 결과와 task definition revision만 남긴다. certificate PEM, private key, secret value와 bearer token은 남기지 않는다.

## 10. Rotation과 rollback

### Leaf rotation

1. 새 leaf bundle을 `AWSPENDING`으로 입력한다.
2. one-off verifier task가 exact version stage를 로드해 chain/SAN/EKU/handshake를 확인한다.
3. `AWSCURRENT`를 새 version으로 이동하고 이전 version은 `AWSPREVIOUS`로 보존한다.
4. 해당 ECS Service만 force new deployment한다.
5. loader success, task health와 service-to-service metric을 확인한다.
6. 실패하면 staging label을 이전 version으로 복원하고 다시 force deployment한다.

### CA rotation

1. old+new CA trust bundle을 모든 service에 먼저 배포한다.
2. 새 CA leaf를 caller/callee 순서로 배포한다.
3. 모든 old leaf가 사라진 증거 뒤 old CA를 trust bundle에서 제거한다.

세 단계를 한 deployment로 합치지 않는다. 이전 task definition과 secret version은 검증 window 동안 유지한다.

## 11. 작업 배정과 충돌 경계

현재 계획은 agent 1개가 순차 구현한다.

- PKI/loader owner: `scripts/pki/nonprod/**`, `cert-loader/**`
- AI owner: `ai/envoy/**`, AI auth middleware/tests
- Platform owner: Terraform modules/environment/tests
- Integration owner: shared contracts, tasks/implement/runbook과 최종 검증

`infra/aws/environments/nonprod-v2/locals.tf`, `modules/ecs-task/**`, `.github/workflows/ci.yml`, `specs/002-bff-auth-msa/tasks.md`는 integration owner가 순차 수정하는 shared file이다. 병렬 에이전트를 사용할 경우 이 네 경계는 분할하지 않는다.

## 12. 완료 정의

다음이 모두 충족돼야 T047-B/C/D를 완료로 표시한다.

- offline CA private material이 repository/Terraform/AWS log에 없다는 scan 증거
- 5개 TLS bundle의 exact identity 계약과 loader fail-closed 검증
- 7개 runtime image의 ARM64 digest와 HIGH/CRITICAL finding 0
- Java full regression과 AI/Envoy local mTLS matrix 통과
- Terraform source validation과 no-destructive-change plan 검토
- AWS private validation deployment의 positive/negative/IAM 증거
- leaf rotation rollback과 CA overlap drill
- Core/AI의 ECS shared token 제거
- `internal_mtls_runtime_verified=true` 전환 근거가 `implement.md`에 기록됨

Source 검증만 통과한 상태에서는 `enable_runtime_services=false`, `runtime_gates_acknowledged=false`, `internal_mtls_runtime_verified=false`를 유지한다.
