# External API Reliability Policy

이 문서는 MeetingMind가 의존하는 외부 API와 내부 원격 서비스에 대해 timeout, retry, fallback, 사용자 메시지, 로깅 규칙을 고정한다.

## Scope

- Google OAuth credential 검증
- target Auth JWKS 조회
- LiveKit token 발급 및 signalling 연동
- Soniox / OpenAI / Clova STT
- Backend/Core -> AI 서비스 호출
- AI 서비스 -> text generation provider 호출
- AI 서비스 -> embedding provider 호출
- PostgreSQL / pgvector
- Redis session store
- SMTP reset mail

## Global Rules

1. retry는 idempotent read 또는 queued background job에만 허용한다.
2. save, confirm, status transition 같은 mutation은 자동 retry하지 않는다.
3. provider 원문 오류, credential, prompt, transcript, answer, token은 사용자 응답과 로그에 노출하지 않는다.
4. 모든 5xx 계열 장애는 `traceId`를 포함한 공통 오류 body로 정규화한다.
5. 권한 검사는 provider 호출 전에 끝나야 한다.
6. fallback이 있더라도 이미 저장된 segment, report, task candidate를 중복 생성하면 안 된다.

## Dependency Matrix

| Dependency | Caller | Current Timeout | Retry Policy | Fallback | User-facing Response | Logging Rule |
| --- | --- | --- | --- | --- | --- | --- |
| Google OAuth credential 검증 | Auth/Core | provider verify call 기준, 자동 재시도 없음 | 없음 | 없음 | `401 INVALID_CREDENTIALS` 또는 `401 UNAUTHORIZED` | credential/token 원문 금지, clientId mismatch만 유형으로 기록 |
| Auth target JWKS | Backend/Core | `meetingmind.auth.target.jwks-request-timeout=2s` | safe read 1회 재시도 후보이나 현재 자동 retry 없음 | 없음 | 인증 실패 시 `401 UNAUTHORIZED`, JWKS fetch 장애는 인증 실패로 처리 | issuer/jwks uri/timeout 유형만 기록, token 원문 금지 |
| LiveKit token 발급 | BFF/Core | BFF connect/read timeout policy 사용 | idempotent token fetch만 사용자 수동 재시도 허용, 자동 retry 없음 | 없음 | `503 LIVEKIT_NOT_CONFIGURED` 또는 `503 SERVICE_UNAVAILABLE` | token 값 금지, meetingId/userId/latency/status만 기록 |
| Soniox realtime STT | Backend live session | provider session 기준 | reconnect 허용, committed transcript duplicate 금지 | OpenAI realtime STT if configured | 세션 내 reconnecting/failed 상태 표시, 최종 장애는 `503 STT_PROVIDER_UNAVAILABLE` | transcript 원문 금지, provider/status/latency/segment count만 기록 |
| OpenAI realtime STT | Backend live session | provider session 기준 | reconnect 허용, committed transcript duplicate 금지 | 없음 또는 Soniox가 primary인 경우 역방향 fallback 없음 | 세션 내 reconnecting/failed 상태 표시, 최종 장애는 `503 STT_PROVIDER_UNAVAILABLE` | transcript 원문 금지 |
| Clova STT batch/smoke | Backend transcription gateway | `10s` (`HttpTranscriptionGateway.REQUEST_TIMEOUT`) | safe polling/read만 허용, mutation retry 없음 | 없음 | `503 STT_PROVIDER_UNAVAILABLE` | audio path/secret 금지, provider/status/latency만 기록 |
| Meeting AI / Project AI internal service | Backend/Core -> AI | `30s` (`HttpMeetingAiGatewayClient`, `HttpProjectAiGatewayClient`, `HttpKnowledgeGraphGatewayClient`) | 자동 retry 없음 | 없음 | `503 AI_PROVIDER_UNAVAILABLE` | question/source text 금지, endpoint/meetingId/spaceId/source count/status만 기록 |
| Report / Task AI internal service | Backend/Core -> AI | `60s` (`HttpReportAiGatewayClient`, `HttpTaskAiGatewayClient`) | 자동 retry 없음 | 없음 | `503 AI_PROVIDER_UNAVAILABLE` | markdown/source text/provider raw body 금지 |
| AI text generation provider | AI service | provider timeout 기준 `DEFAULT_TIMEOUT_SECONDS` | no-evidence/low-relevance는 provider 미호출, 호출 후 자동 retry 없음 | unsupported 응답 또는 provider unavailable | 근거 없음은 `200 unsupported=true`, timeout/connection/malformed output은 `503 AI_PROVIDER_UNAVAILABLE` | prompt/source/answer/api key 금지, model/source count/unsupported reason/latency만 기록 |
| AI embedding provider | AI service / worker | provider timeout 기준 | queued job backoff retry 허용 | local deterministic provider는 테스트 전용 | 사용자에는 색인 지연 또는 `embeddingStatus=FAILED/PENDING` | embedding text/vector/api key 금지, job id/provider/status/attempt count만 기록 |
| PostgreSQL / pgvector | Backend/Core/AI | query timeout 및 transaction 경계 | transaction-level safe retry만 허용, mutation 자동 retry 없음 | 없음 | `503 SERVICE_UNAVAILABLE` 또는 색인 지연 상태 | SQL/raw payload 금지, table/operation/latency/error type만 기록 |
| Redis session store | BFF | client timeout/pool 정책 | 자동 retry 없음 | 없음 | `503 SERVICE_UNAVAILABLE` 또는 인증/세션 사용 불가 메시지 | session raw value/token vault plaintext 금지 |
| SMTP reset mail | Auth/BFF | provider timeout 기준 | background send 한정 safe retry 허용 | 없음 | 메일 발송 지연/실패 안내, reset token 자체는 노출 금지 | recipient 전체 주소/secret/token 금지, domain/status/attempt count만 기록 |

## BFF Guard Policy

BFF downstream 호출은 `DownstreamGuard`와 `DownstreamHttpClient`로 보호한다.

- bulkhead: `maxConcurrent`
- circuit open 조건: 연속 실패 `failureThreshold`
- open duration 동안 추가 호출 차단
- half-open probe 1건만 허용
- 5xx, network failure, timeout은 `BffProxyException.unavailable(service)`로 정규화
- 401은 guard failure로 세지지 않고 그대로 인증 실패로 처리

이 정책은 현재 Auth/Core/AI/LiveKit downstream에 공통 적용된다.

### Current Code Mapping

| Policy Area | Main Runtime Mapping | Evidence |
| --- | --- | --- |
| BFF downstream circuit/bulkhead | `bff/src/main/java/com/meetingmind/bff/proxy/DownstreamGuard.java`, `bff/src/main/java/com/meetingmind/bff/proxy/DownstreamHttpClient.java`, `bff/src/main/java/com/meetingmind/bff/proxy/DownstreamService.java` | `bff/src/test/java/com/meetingmind/bff/proxy/DownstreamGuardTest.java`, `bff/src/test/java/com/meetingmind/bff/proxy/DownstreamHttpClientTest.java` |
| BFF normalized downstream 503 | `bff/src/main/java/com/meetingmind/bff/proxy/BffProxyException.java`, `bff/src/main/java/com/meetingmind/bff/auth/BffAuthExceptionHandler.java` | `bff/src/test/java/com/meetingmind/bff/proxy/BffProxyControllerTest.java` |
| BFF Prometheus export | `bff/src/main/java/com/meetingmind/bff/observability/PrometheusScrapeController.java`, `bff/src/main/java/com/meetingmind/bff/observability/DownstreamGuardMetrics.java`, `bff/src/main/resources/application.yml` | `bff/src/test/java/com/meetingmind/bff/BffHealthEndpointTest.java` |
| Backend/Core normalized AI 503 | `backend/src/main/java/com/meetingmind/demo/service/MeetingAiService.java`, `ProjectAiService.java`, `ReportCandidateService.java`, `TaskCandidateService.java`, `MeetingTermExplanationService.java` | `backend/src/test/java/com/meetingmind/demo/domain/MeetingAiServiceTest.java`, `ProjectAiServiceTest.java`, `ReportCandidateServiceTest.java` |
| Backend/Core allowed meeting scope | `backend/src/main/java/com/meetingmind/demo/service/AiSearchScopeResolver.java`, `backend/src/main/java/com/meetingmind/demo/service/ProjectAiService.java`, `backend/src/main/java/com/meetingmind/demo/service/KnowledgeGraphService.java` | `backend/src/test/java/com/meetingmind/demo/domain/ProjectAiServiceTest.java`, `backend/src/test/java/com/meetingmind/demo/service/HttpAiGatewayClientEndpointTest.java` |
| LiveKit unavailable mapping | `backend/src/main/java/com/meetingmind/demo/controller/LiveKitController.java` | `backend/src/test/java/com/meetingmind/demo/domain/MeetingLiveKitTokenServiceTest.java` |
| STT unavailable mapping | `backend/src/main/java/com/meetingmind/demo/controller/MeetingTranscriptionController.java`, `backend/src/main/java/com/meetingmind/demo/service/ConfiguredSttProvider.java` | `backend/src/test/java/com/meetingmind/demo/controller/MeetingTranscriptionControllerTest.java`, `backend/src/test/java/com/meetingmind/demo/domain/ClovaSttTranscriptSmokeIntegrationTest.java` |
| Transcript -> embedding job enqueue | `backend/src/main/resources/db/migration/V12__finalize_vector_search_jobs.sql` | `backend/src/test/java/com/meetingmind/demo/service/SttTranscriptFlowIntegrationTest.java` |
| AI unsupported/provider failure split | `ai/app/grounding.py`, `ai/app/main.py` | `ai/tests/test_meeting_ai.py` |
| AI provider response-format and timeout policy | `ai/app/text_generation_provider.py`, `ai/app/main.py` | `ai/tests/test_meeting_ai.py`, `ai/tests/test_text_generation_provider.py` |
| AI Prometheus export | `ai/app/main.py`, `ai/app/observability.py`, `ai/requirements.txt` | `ai/tests/test_meeting_ai.py` |

## Backend/Core Policy

- Backend/Core -> AI 호출은 endpoint별 고정 timeout을 사용한다.
- Backend/Core -> AI internal HTTP 경계에는 `AiGatewayGuard`가 적용되어 semaphore bulkhead, failure threshold, open duration, half-open probe를 강제한다.
- 남은 범위는 STT gateway와 AI service 내부 provider worker 경계에 동일 수준의 guard/metric을 더 넓히는 것이다.

## STT Policy

### Realtime STT

- primary provider 실패 시 `ConfiguredSttProvider`가 configured fallback provider를 시도할 수 있다.
- fallback 성공 여부와 관계없이 이미 committed 된 segment를 다시 저장하면 안 된다.
- reconnect 중에는 사용자에게 실패로 오인되지 않도록 `connecting/reconnecting` 상태를 먼저 보여준다.

### Transcript Completion

- transcript 완료 시 embedding job은 transcript 단위로 정확히 1건만 생성한다.
- provider reconnect나 callback replay가 있어도 duplicate job 생성은 금지한다.

## AI Response Policy

### Unsupported vs Unavailable

다음은 `unsupported`로 처리한다.

- evidence 0건
- relevance threshold 미달
- source validation 결과 모두 탈락

다음은 `503 AI_PROVIDER_UNAVAILABLE`로 처리한다.

- provider timeout
- provider connection failure
- provider malformed output
- internal AI service unavailable

### Citation Rule

- provider가 반환한 `sourceIds`는 실제 retrieval result의 source ID 부분집합이어야 한다.
- 없는 source ID가 섞이면 supported 응답을 폐기하거나 unsupported로 downgrade 한다.

## User Message Baseline

| Situation | Response Rule |
| --- | --- |
| OAuth credential invalid | 로그인에 실패했습니다. 입력 또는 계정 상태를 확인해주세요. |
| LiveKit unavailable | 실시간 회의 연결을 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해주세요. |
| STT unavailable | 전사를 일시적으로 사용할 수 없습니다. 연결 상태를 확인한 뒤 다시 시도해주세요. |
| AI no evidence | 현재 근거로는 답변할 수 없습니다. 관련 회의록 또는 프로젝트 지식을 확인해주세요. |
| AI provider timeout/failure | AI 응답을 일시적으로 생성할 수 없습니다. 잠시 후 다시 시도해주세요. |
| Redis/session failure | 세션을 확인할 수 없습니다. 다시 로그인해주세요. |
| SMTP failure | 메일을 보내지 못했습니다. 잠시 후 다시 시도해주세요. |

사용자 메시지는 provider 이름, raw status code, stack trace, secret, token 값을 포함하지 않는다.

## Trace and Audit Rule

- 외부 API 장애 응답은 모두 `traceId`를 포함한다.
- `AI_REQUESTED`, `LIVE_TOKEN_ISSUED`, `MEETING_TRANSCRIPTION_STARTED` 같은 감사 이벤트는 성공 시점 기준으로 남긴다.
- provider 장애 자체는 audit event보다 metric/log 중심으로 남기고, 사용자 텍스트는 기록하지 않는다.

## Requirement Trace

- `requirements/non-functional-requirements.md`: `NFR-REL-01`, `NFR-REL-02`, `NFR-AVAIL-02`, `NFR-LOG-01`, `NFR-LOG-02`
- `requirements/performance.md`: `PERF-EXT-01`, `PERF-EXT-02`, `PERF-EXT-03`, `PERF-EXT-04`, `PERF-EXT-05`
- `requirements/policies.md`: `POL-AUTHZ-01`, `POL-REALTIME-01`, `POL-TOKEN-01~06`
- `specs/001-meetingmind-core/ai-harness-strategy.md`

## Open Gaps

1. STT gateway와 AI service 내부 provider worker 경계의 guard/circuit 표준화는 후속이다.
2. Prometheus scrape config, Grafana dashboard json, STT/LiveKit custom metric은 `T439.1` 이후 작업으로 남아 있다.
3. SMTP provider 구체 구현과 retry 횟수는 reset mail 구현 시점에 확정한다.
