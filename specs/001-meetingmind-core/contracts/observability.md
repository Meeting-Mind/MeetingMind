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
- `meetingmind.stt.transcription.start{outcome}` (Timer)
- `meetingmind.stt.transcription.stop{outcome}` (Timer)
- `meetingmind.livekit.token.issue{outcome}` (Timer)
- `meetingmind.report.confirm{outcome}` (Timer)

label에는 식별자를 넣지 않는다. meetingId나 userId를 label로 쓰면 시계열이 무한히 늘어나고
`NFR-LOG-01`의 식별 정보 비노출 원칙과도 충돌한다. `outcome`(success/failure)만 둔다.

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

`infra/grafana/dashboards/meetingmind-stt-live.json`으로 구현했다(`T439.4`).

- 전사 시작/종료 호출 수와 실패율
- 전사 시작/종료 평균 소요 시간
- LiveKit token 발급 수와 평균 소요 시간
- 회의록 확정 수와 평균 소요 시간

## Logging Constraints

- prompt, transcript text, AI answer 원문, API key, token, secret, DSN은 metric label 또는 로그 field에 넣지 않는다.
- traceId로만 요청 추적을 연결한다.

## Gaps

- Backend STT/LiveKit/report-confirm custom metric은 `T439.4`로 구현했고 dashboard에 연결했다. metric 이름은 `BackendMetricNamesTest`가 Prometheus 노출명으로 고정한다.
- Grafana dashboard json/provisioning은 `infra/grafana/`에 구현했다(`T439.2`). AI 지표 8종은 실제 `/metrics` 출력과 대조해 존재를 확인했고, BFF 지표는 Micrometer 등록 코드 기준으로 변환 규칙을 적용했다.
- BFF `/actuator/prometheus`가 `@SpringBootTest` 환경에서 Prometheus 노출 형식이 아닌 텍스트를 반환한다. 기존 테스트가 "비어 있지 않음"만 단정해 드러나지 않았다. 이 상태에서는 dashboard가 쓰는 BFF 지표 이름을 테스트로 고정할 수 없다.
- Prometheus scrape 설정은 인프라 작업으로 분리한다.
