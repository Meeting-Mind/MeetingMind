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

```bash
cd backend
./gradlew test --tests com.meetingmind.demo.domain.MeetingLiveKitTokenServiceTest
./gradlew test --tests com.meetingmind.demo.service.SttTranscriptFlowIntegrationTest
./gradlew test --tests com.meetingmind.demo.domain.MeetingReportLifecycleServiceTest
./gradlew test --tests com.meetingmind.demo.domain.ReportCandidateServiceTest
./gradlew test --tests com.meetingmind.demo.domain.MeetingAiServiceTest
./gradlew test --tests com.meetingmind.demo.domain.ProjectAiServiceTest
```

## Provider Opt-in Checks

### STT Provider Smoke

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
| TBD | TBD | TBD | SMK-002 | Opt-in provider | TBD | transcript/report IDs | TBD |
| TBD | TBD | TBD | SMK-003 | Opt-in provider | TBD | report/knowledge IDs | TBD |
| TBD | TBD | TBD | SMK-004 | Opt-in provider | TBD | answer/source IDs | TBD |
| TBD | TBD | TBD | SMK-005 | Local/manual | TBD | account/meeting IDs | TBD |
