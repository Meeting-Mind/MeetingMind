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

미해결 불일치: 아래 opt-in 검증은 `clova-nest`를 대상으로 한다. 그러나 실제 runtime 기본
provider는 `ConfiguredSttProvider`의 `STT_PROVIDER` 기본값 `soniox-realtime`이고 fallback은
`openai-realtime`이다. 즉 문서가 지정한 유일한 provider 근거와 실제로 실행되는 provider가
다르다. `SMK-002`를 닫기 전에 다음 중 하나를 먼저 결정해야 한다.

- `soniox-realtime` 대상 opt-in smoke를 추가한다. (실제 기본 경로를 검증)
- 또는 smoke 대상 provider를 운영 기본값과 일치시킨다.

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
| TBD | TBD | TBD | SMK-002 (provider tier) | Opt-in provider | BLOCKED | transcript/report IDs | Clova 키 부재 + 문서 지정 provider(`clova-nest`)와 runtime 기본 provider(`soniox-realtime`) 불일치. 위 "STT Provider Smoke" 결정 필요 |
| TBD | TBD | TBD | SMK-003 | Opt-in provider | TBD | report/knowledge IDs | TBD |
| TBD | TBD | TBD | SMK-004 | Opt-in provider | TBD | answer/source IDs | TBD |
| TBD | TBD | TBD | SMK-005 | Local/manual | TBD | account/meeting IDs | TBD |
