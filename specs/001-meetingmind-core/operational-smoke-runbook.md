# Operational Smoke Runbook

## Purpose

This runbook separates repeatable local verification from provider-backed smoke tests.
Default CI and local development must not require paid provider keys. LiveKit, STT,
OpenAI-compatible generation, embedding, and public callback checks are opt-in.

## Smoke Groups

| ID | Flow | Mode | Required external setup | Primary evidence |
| --- | --- | --- | --- | --- |
| SMK-001 | Deterministic AI harness | Local automated | None | AI unit tests and on-prem HTTP smoke unit tests |
| SMK-002 | LiveKit + STT | Local DB plus opt-in provider | LiveKit credential, STT provider key, optional `PUBLIC_WS_BASE_URL` | token issue, live join, STT start/stop, transcript persistence |
| SMK-003 | AI Report to Knowledge | Local DB plus opt-in provider | completed transcript, report provider, embedding provider/worker | confirmed report creates searchable knowledge source |
| SMK-004 | Project AI confirmed report query | Local DB plus opt-in provider | SMK-003 data and Project AI provider | Project AI answers from confirmed report with citation |
| SMK-005 | Guest/ACL negative | Local automated plus browser/manual | guest account variants | guest cannot access Space-wide data outside allowed meeting |

### 현재 상태 (2026-07-26)

| ID | 자동 검증 가능 범위 | 상태 | 근거 |
| --- | --- | --- | --- |
| SMK-001 | 전체 | PASS | AI unit + on-prem HTTP smoke |
| SMK-002 | local tier / STT provider / LiveKit 도달성 | PASS | `V119.4`, `T440`, `T444` |
| SMK-002 | 매체 publish/subscribe join | **수동 대기** | 브라우저 필요, 자동화 불가 |
| SMK-003 | 회의록 본문 생성 | PASS | `T449` |
| SMK-003 | 확정 -> 색인 작업 생성 | PASS | `T445` |
| SMK-003 | worker 소비 -> `report` chunk | PASS | `T447` |
| SMK-004 | 전체 | PASS | `T448` |
| SMK-005 | 서버 ACL 경계 | PASS | `T446` |
| SMK-005 | UI 우회 경로 | **수동 대기** | 브라우저 필요 |

자동화 가능한 범위는 모두 닫혔다. `V119` 마감은 위 **수동 2건**만 남아 있으며, 이 둘은
브라우저 실조작이 필요해 에이전트가 대신 수행할 수 없다.

## Local Deterministic Checks

Run these before provider smoke. They should not require paid provider keys.

```bash
cd ai
./.venv/bin/python -m unittest tests.test_meeting_ai
./.venv/bin/python -m unittest tests.test_onprem_poc_http_smoke
./.venv/bin/python -m compileall app tests/test_meeting_ai.py tests/test_onprem_poc_http_smoke.py
```

Backend checks that use PostgreSQL require `CI_POSTGRES_URL`, `CI_POSTGRES_USER`, and
`CI_POSTGRES_PASSWORD`. Without those env vars, DB integration tests are skipped or not
selected.

주의: env가 없으면 `@EnabledIfEnvironmentVariable` 게이트 때문에 실패가 아니라 skip으로
넘어간다. skip은 통과 근거가 아니므로 실행 후 `tests`/`skipped` 수를 함께 확인한다.

로컬 PostgreSQL은 docker `meetingmind-postgres-local`이며 host port는 5432가 아니라
**5434**다. `application-local.yml`의 `MEETINGMIND_DB_PORT` 기본값과 같다.

```bash
cd backend && CI_POSTGRES_URL="jdbc:postgresql://localhost:5434/meetingmind" CI_POSTGRES_USER="meetingmind" CI_POSTGRES_PASSWORD="meetingmind_local" ./gradlew test --tests com.meetingmind.demo.domain.SttTranscriptFlowIntegrationTest
```

```bash
cd backend
./gradlew test --tests com.meetingmind.demo.domain.MeetingLiveKitTokenServiceTest
./gradlew test --tests com.meetingmind.demo.service.SttTranscriptFlowIntegrationTest
./gradlew test --tests com.meetingmind.demo.domain.MeetingReportLifecycleServiceTest
./gradlew test --tests com.meetingmind.demo.domain.ReportCandidateServiceTest
./gradlew test --tests com.meetingmind.demo.domain.MeetingAiServiceTest
./gradlew test --tests com.meetingmind.demo.domain.ProjectAiServiceTest
```

## Local Runtime Baseline

Provider smoke 전에 local runtime 경로가 먼저 안정적으로 떠야 한다.

- Backend: `./scripts/run-backend.sh`
- BFF legacy: `./scripts/run-bff-legacy.sh`
- Frontend: `./scripts/run-frontend.sh`
- AI: `./scripts/run-ai.sh`

주의:

- AI는 system `python3 -m uvicorn`이 아니라 project virtualenv의
  `ai/.venv/bin/uvicorn`을 우선 사용해야 한다.
- local stack helper는 smoke 진입 전 포트 점유 상태를 먼저 확인해야 한다.
- stale listener가 남아 있으면 LiveKit/STT/AI smoke가 false negative로 보일 수 있다.

## Provider Opt-in Checks

### STT Provider Smoke

provider별로 opt-in smoke가 나뉜다. 실제 runtime 기본 provider는 `ConfiguredSttProvider`의
`STT_PROVIDER` 기본값인 **`soniox-realtime`**이므로, 기본 경로 검증은 Soniox smoke로 한다.
`clova-nest`는 별도 자격증명이 있을 때만 실행한다. (`T440`)

#### Soniox Realtime Smoke (runtime 기본 경로)

`SonioxSttTranscriptSmokeIntegrationTest`는 기본 비활성이다.

| Env | Purpose |
| --- | --- |
| `RUN_SONIOX_STT_SMOKE=true` | Soniox provider smoke 활성화 |
| `SONIOX_API_KEY` | Soniox 자격증명 |
| `SONIOX_STT_SMOKE_PCM_PATH` | PCM s16le, mono, 16 kHz raw 샘플 경로 (WAV 헤더 없음) |
| `SONIOX_STT_SMOKE_EXPECTED_TEXT` | (선택) 특정 문구까지 확인할 때 |
| `CI_POSTGRES_URL` / `CI_POSTGRES_USER` / `CI_POSTGRES_PASSWORD` | 테스트 DB (`scripts/run-db-tests.sh`가 설정) |

```bash
RUN_SONIOX_STT_SMOKE=true SONIOX_STT_SMOKE_PCM_PATH=/path/to/sample.pcm ./scripts/run-db-tests.sh --tests com.meetingmind.demo.domain.SonioxSttTranscriptSmokeIntegrationTest
```

Pass criteria:

- 실제로 `soniox-realtime`을 탔다. (fallback 대체가 아님)
- Transcript status becomes `COMPLETED`이고 provider가 `soniox-realtime`으로 기록된다.
- Segment가 1건 이상이고 전사 텍스트가 비어 있지 않다.
- `TRANSCRIPT_COMPLETED` embedding job이 정확히 1건 enqueue된다.

거짓 양성 주의: `ConfiguredSttProvider`는 primary 생성 실패 시 `STT_FALLBACK_PROVIDER`
(기본 `openai-realtime`)로 넘어간다. 그대로 두면 Soniox가 실패했는데 OpenAI로 대체되어
통과할 수 있다. 테스트는 fallback을 primary와 같게 고정하고 실제 `providerId()`를 단정해
이 경로를 막는다.

샘플 준비 주의: `backend/output/debug-audio/*.wav`는 실제 회의 녹음이므로 외부 provider
smoke 입력으로 쓰지 않는다. 합성 음성으로 만들면 기대 문구를 알 수 있어 검증도 강해진다.
macOS 예시는 `say`로 만든 뒤 `afconvert`로 16 kHz mono s16le WAV로 바꾸고 WAV 헤더를
제거해 raw PCM으로 저장한다.

로그 확인은 자동 단정이 아니라 수동 항목이다. 실행 후 provider 원문 오류, API key,
raw audio 경로가 사용자 노출 출력에 남지 않았는지 로그에서 직접 확인한다.

#### LiveKit Real Server Smoke

`LiveKitRealServerSmokeIntegrationTest`는 기본 비활성이며 `RUN_LIVEKIT_SMOKE=true`일 때만
실행된다. DB를 쓰지 않으므로 `run-db-tests.sh` 없이 실행할 수 있다.

| Env | Purpose |
| --- | --- |
| `RUN_LIVEKIT_SMOKE=true` | LiveKit 실서버 smoke 활성화 |
| `LIVEKIT_URL` (또는 `LIVEKIT_WS_URL`) | LiveKit 서버. `wss://`는 자동으로 `https://` API URL로 변환된다 |
| `LIVEKIT_API_KEY` / `LIVEKIT_API_SECRET` | 서버 자격증명 |

```bash
cd backend && RUN_LIVEKIT_SMOKE=true ./gradlew cleanTest test --tests com.meetingmind.demo.service.LiveKitRealServerSmokeIntegrationTest
```

Pass criteria:

- 실서버에 room이 생성되고 `listRooms`로 조회된다. (자격증명 유효 + 서버 도달)
- 발급 token의 `iss`가 API key이고 `video.room`이 대상 room으로 스코프된다.
- 삭제 후 조회에서 room이 남지 않는다.

범위 한계: 매체 publish/subscribe는 검증하지 않는다. 실제 오디오/비디오 join은 브라우저
client가 필요하므로 아래 product E2E 수동 절차에 남는다. `MeetingLiveKitTokenServiceTest`는
mock 기반이라 권한 분기만 검증하며 실접속 근거가 아니다.

#### Clova Nest Smoke (자격증명 보유 시)

`ClovaSttTranscriptSmokeIntegrationTest` is intentionally disabled by default.

Required env:

| Env | Purpose |
| --- | --- |
| `RUN_CLOVA_STT_SMOKE=true` | Enables the real provider smoke test |
| `CLOVA_STT_SMOKE_PCM_PATH` | PCM 16 kHz mono sample path |
| `CI_POSTGRES_URL` | PostgreSQL JDBC URL |
| `CI_POSTGRES_USER` | PostgreSQL user |
| `CI_POSTGRES_PASSWORD` | PostgreSQL password |

Command:

```bash
cd backend
RUN_CLOVA_STT_SMOKE=true ./gradlew test --tests com.meetingmind.demo.domain.ClovaSttTranscriptSmokeIntegrationTest
```

Pass criteria:

- Transcript status becomes `COMPLETED`.
- At least one transcript segment is persisted.
- `TRANSCRIPT_COMPLETED` embedding job is enqueued once.
- Provider raw error, API key, and raw audio path are not logged as user-facing output.

### AI On-prem / OpenAI-compatible Smoke

The AI smoke runner supports preflight and final smoke. Preflight checks env shape
without provider/RAG calls.

```bash
cd ai
ONPREM_POC_PREFLIGHT_ONLY=true ./onprem_poc_run.sh ./onprem.env.example
```

Final smoke requires real local OpenAI-compatible generation and embedding endpoints,
PostgreSQL/pgvector retrieval data, and `AI_INTERNAL_SERVICE_TOKEN`.

Required final env includes:

| Env | Purpose |
| --- | --- |
| `RUN_ONPREM_AI_POC_SMOKE=true` | Enables final smoke |
| `AI_TEXT_PROVIDER=local-openai-compatible` | Text provider boundary |
| `AI_TEXT_BASE_URL` | Local generation endpoint |
| `AI_TEXT_MODEL` | Non-placeholder text model |
| `AI_TEXT_STREAM=true` | TTFT and streaming validation |
| `AI_EMBEDDING_PROVIDER=local-openai-compatible` | Embedding provider boundary |
| `AI_EMBEDDING_BASE_URL` | Local embedding endpoint |
| `AI_EMBEDDING_MODEL` | Non-placeholder embedding model |
| `AI_EMBEDDING_DIMENSION` | Vector dimension |
| `AI_DATABASE_URL` | PostgreSQL/pgvector retrieval DB |
| `AI_INTERNAL_SERVICE_TOKEN` | Internal API authorization |
| `ONPREM_POC_PROJECT_ID` | Retrieval scope |
| `ONPREM_POC_ALLOWED_MEETING_IDS` | Retrieval meeting scope |

Command:

```bash
cd ai
./onprem_poc_run.sh ./onprem.env.local
```

Pass criteria:

- Meeting AI, Project AI, report, task, unsupported, and permission scenarios pass.
- Retrieval returns scoped sources only.
- Provider model, duration, and source counts are recorded without prompt/answer/secret text.
- Validator passes against the generated result JSON.

### Embedding Worker Smoke (SMK-003 provider tier)

Embedding worker는 FastAPI app(`app.main`)이 아니라 **독립 프로세스**다. `scripts/run-ai.sh`는
worker를 띄우지 않고, `compose.local.yml`의 `meetingmind-ai-worker`는 `profiles: ["ai"]` 뒤에
있어 기본 `docker compose up -d`로는 뜨지 않는다. 그래서 local에서 `embedding_jobs`가 소비되지
않고 PENDING으로 쌓인다. `app/main.py`만 보면 worker가 없는 것처럼 보이니 주의한다.

```bash
./scripts/run-embedding-worker.sh
```

실제 embedding provider를 호출하므로 **OpenAI 과금이 발생한다**. 기본값은
`AI_EMBEDDING_PROVIDER=openai`, `OPENAI_EMBEDDING_MODEL=text-embedding-3-small`,
dimension 1536이며 `OPENAI_API_KEY`는 루트 `.env`에서 읽는다.

Pass criteria:

- `REPORT_CONFIRMED` 작업이 `COMPLETED`가 되고 로그에 `embedding_job_completed`와 `activated=true`가 남는다.
- `embedding_chunks`에 `source_type='report'`, `is_active=true`, `vector_dims(embedding)=1536`인 행이 생긴다.
- 이전 generation chunk가 `is_active=false`로 교체된다.

빈 큐를 폴링하는 것만으로는 통과 근거가 되지 않는다. 반드시 소비된 작업 ID와 chunk 수를
함께 남긴다. 과금 없이 원인을 좁히려면 provider 호출 이전 단계인 `load_snapshot`만 따로
실행해 볼 수 있다.

`INTERNAL_ERROR`는 미분류 예외의 포괄 코드이고 `embedding_jobs`에 메시지 컬럼이 없어
실패 행만으로는 원인을 알 수 없다. 재실행으로 좁히는 것 외의 방법이 현재 없다.

### End-to-end Product Smoke

Use this only after backend, BFF legacy mode, frontend, PostgreSQL, Redis, LiveKit, and
STT provider are running.

1. Create or choose a Space and meeting.
2. Enter prejoin, verify camera/mic readiness, then enter live room.
3. Start STT and speak a short scripted meeting.
4. Confirm the transcript is visible and persisted after refresh.
5. Generate AI Report from transcript.
6. Confirm report.
7. Confirm Project Knowledge/RAG source exists for the confirmed report.
8. Ask Project AI a question answerable only from the confirmed report.
9. Ask a guest account to access an unrelated Space/meeting and verify denial.

## Failure Handling Rules

- Provider smoke failures must report the dependency, normalized error code, trace ID,
  and retry/next action.
- Do not retry state-changing actions such as report confirm automatically.
- Do not log raw prompt, STT text, report content, answer text, token, API key, or LiveKit
  token value.
- If retrieval or provider is down, AI must return unsupported or service unavailable
  without widening scope.
- If `allowedMeetingIds` is empty, Project AI must not search meeting sources.

## Execution Record Template

| Date | Tester | Build/branch | Smoke ID | Mode | Result | Evidence | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-07-25 | Codex | current working tree | SMK-001 | Local automated | PASS | `cd ai && ./.venv/bin/python -m unittest tests.test_meeting_ai`, `cd ai && ./.venv/bin/python -m unittest tests.test_onprem_poc_http_smoke`, `cd backend && ./gradlew test --tests com.meetingmind.demo.domain.MeetingReportLifecycleServiceTest`, `cd backend && ./gradlew test --tests com.meetingmind.demo.domain.ProjectAiServiceTest` | `tests.test_onprem_poc_http_smoke`는 provider env 부재로 1건 skip, 나머지 deterministic 검증은 통과 |
| 2026-07-26 | Claude | `docs/ai-harness-test-matrix` @ 1b4dffc + local fixes | SMK-002 (local tier) | Local DB automated | PASS | `SttTranscriptFlowIntegrationTest` 1건 실행/0 skip, `MeetingLiveKitTokenServiceTest` 5건 실행/0 skip | 실제 PostgreSQL(5434)에서 transcript `COMPLETED` 전이, segment 순서/speaker 보존, `embedding_jobs` `TRANSCRIPT_COMPLETED` 1건 enqueue(DB trigger 경로)를 확인. LiveKit은 mock 기반이라 실제 서버 접속 근거는 아님. 이 검증은 `@Primary` 충돌로 그동안 깨져 있었고 env 미설정으로 skip되어 드러나지 않았음(V119.4) |
| 2026-07-26 | Claude | `feat/soniox-stt-smoke` @ becc81d+ | SMK-002 (provider tier, STT) | Opt-in provider | PASS | `SonioxSttTranscriptSmokeIntegrationTest` 1건 실행/0 skip. meeting `meeting-6e842ab8-ee8f-4b05-8534-68080350111a`, status `COMPLETED`, provider `soniox-realtime`, segments 2, `TRANSCRIPT_COMPLETED` embedding job 1 | 실제 Soniox realtime API 호출. 입력은 합성 한국어 음성(16 kHz mono s16le, 약 5초)이며 실제 회의 녹음을 쓰지 않았다. `providerId()` 단정으로 fallback 대체가 아님을 확인했다. 전사 결과가 입력 문구와 일치했다 |
| 2026-07-26 | Claude | `feat/soniox-stt-smoke` | SMK-002 (provider tier, LiveKit) | Opt-in provider | PASS (server-side) | `LiveKitRealServerSmokeIntegrationTest` 1건 실행/0 skip. room create/list/delete 왕복, token `iss`=API key, `video.room` 스코프 일치 | 실제 LiveKit Cloud 호출로 자격증명 유효성과 서버 도달성을 확인했다. room은 삭제로 정리하고 조회로 잔존 없음을 단정한다. 매체(media) publish/subscribe는 브라우저 client가 필요하므로 product E2E 수동 절차로 남는다 |
| TBD | TBD | TBD | SMK-002 (provider tier, Clova) | Opt-in provider | N/A | — | `clova-nest` 자격증명 부재. runtime 기본 provider가 아니므로 `SMK-002` 종료 조건에서 제외한다 |
| 2026-07-26 | Claude | `feat/soniox-stt-smoke` | SMK-003 (local tier, 색인 연결) | Local DB automated | PASS | `ReportConfirmKnowledgeIndexIntegrationTest` 1건 실행/0 skip | 앱 경로 `confirmMeetingReport`가 `REPORT_CONFIRMED` 색인 작업 1건을 만든다. CANDIDATE에서는 생기지 않음도 확인. 확정 회의록은 별도 `project_knowledge` 문서가 아니라 meeting source로 색인된다 |
| 2026-07-26 | Claude | `feat/smk005-guest-acl-negative` | SMK-003 (provider tier, embedding worker) | Opt-in provider (OpenAI 과금) | PASS | job `embedding-job-f4cd7b44...`(7 chunk), `embedding-job-90559ff3...`(9 chunk) | worker는 구현되어 있었고 local 실행 경로가 없었다(`run-ai.sh`는 FastAPI만, compose worker는 `profiles: ["ai"]`). `scripts/run-embedding-worker.sh` 추가 후 `REPORT_CONFIRMED` 2건이 각각 1회 시도로 COMPLETED, active `report` chunk 3건 전부 1536차원 vector 보유. 기존 `INTERNAL_ERROR` 실패는 환경성이었고 재실행으로 해소됐다. 회의록 **본문 AI 생성**은 여전히 별도 단계다 |
| 2026-07-26 | Claude | `feat/smk003-embedding-worker` | SMK-003 (본문 생성) | Opt-in provider (OpenAI 과금) | PASS | `meeting-1b4438c0…` transcript 37건 -> markdown 976자, decision 3 / actionItem 3 / model `gpt-4.1-mini-2025-04-14` | `/api/internal/meeting-ai/generate-report`는 자체 검색을 하지 않고 Backend가 전달한 source만 쓴다. `unsupported=false`이고 인용 6건이 전부 전달 범위 안(범위 밖 0건). 음성 2건은 HTTP 403 `AI_CONTEXT_FORBIDDEN`(다른 회의 source 혼입 / 허용되지 않은 source type). 응답 필드는 `supported`가 아니라 `unsupported`다 |
| 2026-07-26 | Claude | `feat/smk003-embedding-worker` | SMK-004 | Opt-in provider (OpenAI 과금) | PASS | 인용 `report-738390bc…`, `report-741bdeb4…` / model `gpt-4.1-mini-2025-04-14` | Project AI가 확정 회의록을 인용해 답한다. 양성 2건(서로 다른 Space) + 음성 3건(`LOW_RELEVANCE`, `NO_EVIDENCE`, 교차 Space)으로 검증. unsupported 응답은 sources를 비우므로 응답만으로는 SQL 누출 여부를 알 수 없어, `PostgresRagRetriever`를 직접 호출해 모델과 무관하게 scope 강제를 확인했다(양성 8건 확보, 교차 누출 0건) |
| 2026-07-26 | Claude | `feat/smk005-guest-acl-negative` | SMK-005 (server ACL) | Local DB automated | PASS | `GuestSpaceAclNegativeIntegrationTest` 1건 실행/0 skip | 실 DB에서 회의 전용 GUEST가 초대 밖 회의 상세, Space 회의/멤버/knowledge 목록, Space 상세 전부 거부됨을 확인. 회의 참가가 Space 멤버십으로 승격되지 않음도 확인. 양성 대조로 자기 회의 읽기는 성공하는 것을 먼저 단정한다 |
| TBD | TBD | TBD | SMK-005 (browser) | Manual | TBD | account/meeting IDs | UI가 서버 경계를 우회하는 경로는 브라우저 수동 확인이 남는다 |
