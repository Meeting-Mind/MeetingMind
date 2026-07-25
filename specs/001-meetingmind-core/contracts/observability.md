# Observability Baseline

이 문서는 MeetingMind의 AI, BFF, Backend 운영 관측 기준선을 정의한다.

## Scope

- BFF `/actuator/prometheus`
- Backend `/actuator/prometheus`
- AI `/metrics`

## Required Metrics

### BFF

- `meetingmind.bff.browser.requests`
- `meetingmind.bff.refresh`
- `meetingmind.bff.session.invalid`
- `meetingmind.bff.downstream.guard.rejections{service=*}`
- `meetingmind.bff.downstream.guard.opened{service=*}`
- `meetingmind.bff.downstream.guard.open{service=*}`

### Backend

- Spring Boot actuator 기본 JVM/HTTP/processor metrics
- 이후 추가 대상:
  - STT callback latency
  - LiveKit token issue latency/failure
  - report confirm latency/failure

### AI

- `meetingmind_ai_requests_total{endpoint,outcome}`
- `meetingmind_ai_request_duration_ms{endpoint}`
- `meetingmind_ai_request_source_count{endpoint}`
- `meetingmind_ai_provider_requests_total{provider,api_style,stream,outcome}`
- `meetingmind_ai_provider_total_ms{provider,api_style,stream}`
- `meetingmind_ai_provider_tokens_total{provider,api_style,stream,direction}`
- `meetingmind_ai_rag_retrieval_duration_ms{scope,outcome}`
- `meetingmind_ai_rag_retrieval_result_count{scope}`
- `meetingmind_ai_embedding_queue{status}`

## Dashboard Panels

### AI Overview

- endpoint별 request count / failure rate
- endpoint별 p50/p95 duration
- provider별 total ms
- provider별 input/output token usage
- unsupported 응답 비율

### RAG

- meeting/project retrieval p50/p95
- retrieval result count distribution
- embedding queue pending/processing/failed

### BFF/Downstream

- service별 guard rejection count
- service별 circuit open gauge
- browser request outcome rate

### STT/Live

- 현재는 dashboard 기준만 정의하고, 세부 metric 추가는 후속이다.
- 최소 필요 패널:
  - STT provider latency/failure
  - LiveKit token latency/failure
  - transcript completion rate

## Logging Constraints

- prompt, transcript text, AI answer 원문, API key, token, secret, DSN은 metric label 또는 로그 field에 넣지 않는다.
- traceId로만 요청 추적을 연결한다.

## Gaps

- Backend STT/LiveKit custom metric은 아직 미구현이다.
- Grafana dashboard json/provisioning은 아직 미구현이다.
- Prometheus scrape 설정은 인프라 작업으로 분리한다.
