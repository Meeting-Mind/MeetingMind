# AI Harness Strategy

## Purpose

AI harness는 MeetingMind AI가 올바른 범위의 근거만 사용하고, 근거가 없거나 외부 provider가 실패했을 때 안전하게 응답하는지 검증하는 운영 기준이다. 이 문서는 Meeting AI, Project AI, AI Report, Task extraction, RAG retrieval, Terms Dictionary의 공통 안전장치와 테스트 기준을 정의한다.

## Scope

| Area | Harness Target | Main Risk |
| --- | --- | --- |
| Meeting AI | 현재 회의 transcript/report/decision/action item만 사용 | 다른 회의 또는 Project Knowledge 혼입 |
| Project AI | 공식 Project Knowledge와 접근 가능한 회의만 사용 | 권한 없는 회의 chunk 노출 |
| AI Report | 현재 회의 transcript 기반 보고서 생성·수정·확정 | 근거 없는 요약, source ID 위조 |
| Task Extraction | 회의 report/transcript 기반 후보 생성 | source 없는 task 후보 저장 |
| Terms Dictionary | 등록 용어 exact match와 설명 제공 | LLM 불필요 호출, 잘못된 용어 설명 |
| RAG Retrieval | permission prefilter 후 vector/keyword search | 검색 후 필터링으로 인한 권한 누수 |

## Harness Layers

| Layer | Rule | Current Status | Required Evidence |
| --- | --- | --- | --- |
| Service boundary | Browser는 AI service를 직접 호출하지 않고 BFF/Core를 경유한다. AI internal endpoint는 service credential을 요구한다. | Partial | BFF/Core route test, AI internal token negative test |
| Scope envelope | Meeting AI는 `spaceId + meetingId`, Project AI는 `spaceId + allowedMeetingIds`를 명시한다. `allowedMeetingIds=[]`는 전체가 아니라 회의 source 0개다. | Partial | Meeting/Project scope integration test |
| Permission prefilter | Backend/Core가 RAG 검색 전에 Space/Meeting 권한을 계산한다. Frontend filter는 보안 경계가 아니다. | Partial | denied user RAG negative test |
| Retrieval gate | evidence 0건 또는 relevance 미달이면 provider를 호출하지 않는다. | Implemented/Partial | provider spy test, low relevance fixture |
| Structured output | provider 응답은 strict JSON schema로 받고 `supported`, `answer`, `sourceIds`를 검증한다. | Implemented/Partial | invalid source ID rejection test |
| Citation validation | 지원 응답은 실제 search result source ID만 인용한다. public `sources[]`는 실제 사용한 source만 포함한다. | Implemented/Partial | citation accuracy test |
| Prompt injection guard | transcript, report, knowledge 본문은 untrusted context다. source 안의 역할 변경·명령은 무시한다. | Partial | injection fixture test |
| Token budget | context source 수와 token 상한을 두고 낮은 score evidence부터 줄인다. 줄이는 과정에서 권한 범위는 넓히지 않는다. | Gap | token budget unit test |
| Failure policy | no evidence는 `200 unsupported=true`, provider timeout/connect/malformed output은 `503 AI_PROVIDER_UNAVAILABLE`로 정규화한다. | Partial | timeout/provider malformed test |
| Observability | endpoint, duration, model, token usage, source count, unsupported reason, timeout을 기록하고 prompt/STT/answer/key/token은 로그에 남기지 않는다. | Partial | metric/log redaction test |

## Required AI Harness Tests

| ID | Scenario | Input | Expected |
| --- | --- | --- | --- |
| AH-001 | Meeting AI scope | 같은 Space의 A/B 회의, 사용자는 A 회의 participant | A 회의 질문은 A source만 검색하고 B source는 후보에도 없음 |
| AH-002 | Meeting AI deny | 사용자가 active MeetingParticipant가 아님 | RAG 검색과 provider 호출 전 403/404 계열로 차단 |
| AH-003 | Project AI allowed meetings | `allowedMeetingIds`에 A만 포함 | ProjectKnowledge와 A 회의 source만 검색 |
| AH-004 | Empty allowed meetings | `allowedMeetingIds=[]` | 회의 source 0건, ProjectKnowledge만 검색 |
| AH-005 | No evidence | 검색 결과 0건 | provider 미호출, `unsupported=true`, reason `NO_EVIDENCE` |
| AH-006 | Low relevance | 검색 결과는 있으나 threshold 미달 | provider 미호출, `unsupported=true`, reason `LOW_RELEVANCE` |
| AH-007 | Invalid citation | provider가 없는 source ID를 반환 | supported 응답 폐기 또는 `UNVERIFIED_OUTPUT` |
| AH-008 | Prompt injection | source text에 role 변경 또는 지시문 포함 | 지시문 무시, source 근거 범위만 답변 |
| AH-009 | Token budget | source가 token 상한을 초과 | 낮은 score evidence부터 제거, 권한 밖 source 추가 없음 |
| AH-010 | Provider timeout | text generation provider timeout | `503 AI_PROVIDER_UNAVAILABLE`, raw provider detail 미노출 |
| AH-011 | Report source validation | report/task candidate가 source 없는 항목 포함 | invalid 항목 제거, 전부 invalid면 unsupported/failure |
| AH-012 | Terms exact match | 등록 용어와 일치하는 transcript token | glossary 설명 반환, LLM 미호출 |
| AH-013 | Project AI history isolation | user/space가 다른 chat history 존재 | 현재 `spaceId + userId` latest history만 untrusted context로 사용 |
| AH-014 | Log redaction | prompt, transcript, answer, API key 포함 요청 | 로그와 metric tag에 원문/secret/token 미포함 |

## Smoke Gates

| ID | Flow | Pass Criteria |
| --- | --- | --- |
| SMK-001 | Local deterministic RAG | 권한 범위, citation, unsupported branch가 provider 없이 재현된다. |
| SMK-002 | LiveKit + STT | token 발급, 입장, STT start/stop, dialogue polling이 실제 provider와 연결된다. |
| SMK-003 | Report to Knowledge | transcript 완료 후 사용자가 AI report를 생성·확정하면 Project Knowledge/RAG 후보가 생긴다. |
| SMK-004 | Project AI confirmed report | Project AI가 확정 report 기반 질문에 source citation과 함께 답한다. |
| SMK-005 | Guest/ACL negative | 게스트 또는 권한 없는 사용자는 transcript/report/Meeting AI/Project AI 범위 밖 데이터를 볼 수 없다. |

## External API Guardrail Matrix

| External Dependency | Timeout | Retry | Fallback | User Response |
| --- | --- | --- | --- | --- |
| Google OAuth | Required | No automatic retry for credential validation | None | 401 with safe message |
| LiveKit | Required | Safe token fetch retry only if idempotent | None | join/token error with retry action |
| Soniox STT | Required | reconnect policy, no duplicate transcript commit | OpenAI STT if configured | STT reconnecting/failed status |
| OpenAI text generation | Required | No retry for save/confirm, limited retry for idempotent generation if safe | unsupported/provider unavailable | AI temporarily unavailable |
| OpenAI embedding | Required | queued job retry with backoff | deterministic/local in tests only | indexing delayed status |
| AI service | Required | BFF/Core guarded call | None | 503 normalized |
| PostgreSQL/pgvector | Required | transaction-level retry only where safe | None | service unavailable or delayed indexing |
| Redis | Required for BFF session | No unsafe retry | None | session/auth unavailable |
| SMTP | Required for reset flow when enabled | provider-specific safe retry | None | mail send delayed/failed |

## Monitoring Targets

| Metric | Labels | Notes |
| --- | --- | --- |
| `ai_request_duration` | endpoint, model, supported, unsupported_reason | prompt/answer text 금지 |
| `ai_source_count` | endpoint, source_type | source raw text 금지 |
| `ai_token_usage` | endpoint, model, input_bucket, output_bucket | exact sensitive content 금지 |
| `rag_retrieval_duration` | scope, source_type | Meeting/Project scope 분리 |
| `rag_evidence_count` | scope, result_bucket | 권한 밖 후보 수 노출 금지 |
| `stt_provider_latency` | provider, status | transcript 원문 금지 |
| `livekit_token_duration` | status | token value 금지 |
| `downstream_guard_open_total` | service | BFF/Core/AI/LiveKit별 circuit open |
| `provider_error_total` | provider, error_type | raw provider response 금지 |

## Completion Criteria

- AH-001~AH-014가 자동 테스트 또는 명시된 미실행 사유로 추적된다.
- SMK-001~SMK-005가 local/opt-in smoke로 분리된다.
- 외부 API별 timeout, retry, fallback, 사용자 응답이 문서화된다.
- BFF뿐 아니라 Backend/Core -> AI/provider 경계의 circuit/bulkhead 적용 여부가 확인된다.
- Prometheus/Grafana에서 STT, LiveKit, AI, RAG, downstream guard를 볼 수 있는 metric이 정의된다.
