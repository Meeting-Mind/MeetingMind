# meetingmind-cert-loader

`meetingmind-cert-loader`는 ECS task role로 한 서비스의 Secrets Manager TLS bundle을
읽고 검증한 뒤 task-scoped shared volume에 TLS 파일을 기록하는 단발성 init
container다. secret 원문은 환경변수, task definition 또는 로그로 전달하지 않는다.

## 의존성 결정

- Go 1.26 표준 `crypto/x509`, `encoding/json`, `os.Root`: 인증서 검증, strict JSON과
  traversal-resistant 원자 파일 쓰기를 담당한다.
- AWS SDK for Go v2 `config`와 `service/secretsmanager`: ECS task role의 기본 credential
  chain과 `GetSecretValue`를 공식 지원한다.

AWS CLI image와 shell/`jq`/OpenSSL 조합은 최소 runtime image, strict schema, 오류
redaction과 세 파일 원자 쓰기를 하나의 지원 가능한 binary로 제공하지 못한다. 직접
SigV4/credential provider를 구현하는 대안은 보안 위험과 유지보수 범위가 더 크므로
사용하지 않았다. 추가 AWS SDK 모듈은 위 두 개로 제한한다.

## 입력 계약

모든 flag는 필수다. `expected-dns-san`은 BFF에서는 생략하고, `expected-eku`는 manifest
순서대로 반복한다.

```text
--secret-arn <full service TLS bundle ARN>
--version-stage AWSCURRENT|AWSPENDING|AWSPREVIOUS
--expected-service bff|auth|core|ai|stt
--expected-spiffe-id <exact approved SPIFFE ID>
--expected-dns-san <exact DNS SAN, repeatable>
--expected-eku clientAuth|serverAuth
--output-dir /run/meetingmind/tls
```

입력값은 binary에 고정된 NonProd V2 service contract와 다시 비교한다. full ARN은
`aws` partition, `ap-northeast-2`, 12자리 account, 해당 서비스의
`/meetingmind-nonprod-v2/<service>/tls-bundle-XXXXXX` resource만 허용한다.

## 검증과 출력

- JSON schema/metadata와 secret version stage
- leaf+intermediate certificate chain, `(intermediate, root)` 쌍 1~2개의 CA bundle
- CA overlap rotation window에서만 두 번째 쌍을 허용하며, 각 쌍은 자체 CA
  검증을 통과해야 하고 presented intermediate는 정확히 한 쌍과 일치해야 한다.
  중복 intermediate/root, 홀수 개 인증서와 만료된 쌍은 거부한다.
- ECDSA P-256, SHA-256 signature, CA path length와 key usage
- validity, 최대 90일 leaf, certificate/private-key 일치
- 정확히 하나인 SPIFFE URI, exact DNS SAN/EKU
- wildcard DNS, IP/email/추가 SAN type과 unknown EKU 거부

검증 후 group/other writable이 아닌 빈 non-symlink output에서 `.staging-*` 파일을
완성·`fsync`·소유권 변경하고 최종 이름으로 rename한다. rename 중 실패하면 이미
노출한 파일을 제거하고 application container가 시작하지 않도록 실패 종료한다.

```text
/run/meetingmind/tls/tls.key  0400  10001:10001
/run/meetingmind/tls/tls.crt  0444  10001:10001
/run/meetingmind/tls/ca.crt   0444  10001:10001
```

성공 로그에는 leaf SHA-256 fingerprint, expiry와 secret version ID만 남긴다. 실패
로그는 고정 error code만 남기고 AWS 오류, JSON, PEM, private key, ARN과 identity
입력을 출력하지 않는다.

## 로컬 검증

로컬 Go가 없으면 고정한 Go builder image를 사용한다.

```bash
go test ./...
CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build ./cmd/cert-loader
docker buildx build --platform linux/arm64 --load \
  --tag meetingmind-cert-loader:ci cert-loader
trivy image --exit-code 1 --ignore-unfixed --no-progress \
  --scanners vuln --severity HIGH,CRITICAL meetingmind-cert-loader:ci
```

실제 AWS 조회, secret write, Terraform apply와 ECS deployment는 이 단계에서 수행하지
않는다.
