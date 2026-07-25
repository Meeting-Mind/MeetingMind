# meetingmind-ai-envoy

AI Task 안에서 mTLS를 종료하는 Envoy sidecar다. NonProd V2에서 AI로 들어오는
유일한 경로이며 Uvicorn은 loopback `127.0.0.1:8001`에만 bind한다.

## 경계

- `0.0.0.0:8000` downstream TLS는 client certificate를 필수로 요구하고
  `/run/meetingmind/tls`의 cert-loader 산출물(`tls.crt`, `tls.key`, `ca.crt`)을
  사용한다.
- TLS validation과 HTTP RBAC가 모두 exact
  `spiffe://meetingmind.internal/ns/nonprod-v2/sa/meetingmind-core` URI SAN만
  허용한다.
- `forward_client_cert_details: SANITIZE_SET`으로 외부 XFCC를 제거하고 검증된
  현재 certificate의 URI만 재생성해 upstream에 전달한다. FastAPI는
  `AI_INTERNAL_AUTH_MODE=mtls-proxy`에서 이 값을 다시 exact 검증한다.
- admin endpoint는 `127.0.0.1:9901`에만 bind한다. ECS에서는 AI app container의
  health check가 loopback으로 app `/health`와 admin `/ready`를 함께 확인한다.
- access log는 시각/method/path/status/flags/duration/request id와 peer URI
  SAN만 남기고 XFCC, certificate, Authorization, secret 값은 기록하지 않는다.

## 이미지

upstream `envoyproxy/envoy:distroless-v1.38.3`을 manifest digest
`sha256:574348fada8eb1130b448132287d76626dfb07525b16668075382f8e154a45a8`로
고정하고 static config만 복사한다. distroless에는 shell이 없으므로 container
health check는 두지 않는다(liveness는 process, readiness는 app container의
admin `/ready` 확인). `latest` tag와 public registry runtime pull은 사용하지
않으며, runtime은 전용 V2 ECR `ai-envoy` repository에 mirror한 digest만
참조한다. 실행 UID/GID는 `10001:10001`이고 root filesystem은 read-only다.

## 검증

```bash
# config 스키마와 인증서 로드 검증 (일회성 self-signed material)
docker buildx build --platform linux/arm64 --load --tag meetingmind-ai-envoy:ci ai/envoy

# 로컬 mTLS positive/negative matrix (일회성 CA, docker 필요)
ai/envoy/local_mtls_check.sh
```

`local_mtls_check.sh`는 OS 임시 디렉터리의 일회성 CA로 Core 성공, no-cert /
wrong-CA / wrong-SPIFFE 거부, spoofed XFCC 교체와 direct `8001` loopback 경계를
확인하고 종료 시 material과 컨테이너를 모두 삭제한다. 실제 NonProd CA와 AWS
리소스는 사용하지 않는다.
