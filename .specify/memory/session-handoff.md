이 문서는 MeetingMind 팀이 세션을 바꿔도 공유해야 하는 통합 기준만 유지한다.

# Shared Session Handoff

## 목적

- 팀원이 공통으로 알아야 할 병합 기준과 다음 통합 작업을 빠르게 확인한다.
- 상세 작업 이력은 `specs/001-meetingmind-core/implement.md`, 실행 상태는 `tasks.md`, 변경 단위 설명은 PR 본문을 기준으로 한다.
- 개인 이름, 브랜치, 로컬 커밋, 미완료 변경은 이 파일이 아니라 ignored local handoff에 기록한다.

## 공통 기준

- MeetingMind는 `frontend`, `backend`, `ai` 세 영역으로 구성된다.
- Meeting AI public 호출은 Backend의 `POST /api/v1/meetings/{meetingId}/ai/chat`을 경유한다.
- Project AI public 호출은 Backend의 `POST /api/v1/spaces/{spaceId}/ai/chat`을 경유한다.
- AI 서버의 `/api/internal/*` endpoint는 Backend가 권한 검증과 source 선필터를 끝낸 요청만 받는다.
- Meeting AI는 현재 회의의 transcript, current confirmed report, decision, action item만 사용할 수 있다.
- Project AI는 공식 Project Knowledge와 사용자가 읽을 수 있는 회의의 current confirmed report summary만 사용할 수 있다.
- AI source scope가 요청 범위를 벗어나면 `AI_CONTEXT_FORBIDDEN`, 근거가 없으면 추정 답변 대신 `unsupported=true`를 반환한다.
- Frontend는 AI 서버에 직접 context를 보내지 않고 인증된 Backend API에 질문만 보낸다.

## 현재 통합 경계

- `local`/`db` profile의 Auth, Space/Meeting ACL, Transcript/Report/Task/ProjectKnowledge/Audit runtime은 Spring JDBC PostgreSQL repository를 사용한다. `test` profile만 in-memory adapter를 사용한다.
- Auth refresh rotation, owner transfer, join approval, report current 전환, task confirm과 audit는 DB transaction/row lock 경계에 연결되어 있다.
- Project AI meeting 후보는 PostgreSQL에서 active SpaceMember role과 active MeetingParticipant를 선필터한다. Backend는 허용된 source와 `allowedMeetingIds`만 AI 서버에 전달한다.
- PostgreSQL/pgvector semantic RAG 저장소, embedding worker/model/차원/index와 persistent AI request audit는 별도 AI/RAG 후속 작업이다.
- mock/legacy Space와 target Backend Space의 ID 연결은 완료되지 않았으므로 target 목록에 없는 Space에서는 Project AI 호출을 차단한다.
- AI 회의록 candidate는 Backend 편집 권한과 단일 meeting source 검증 뒤에서 생성되고 supported 결과만 PostgreSQL `CANDIDATE`로 저장된다.
- report candidate/draft 확정은 Backend 편집 권한 뒤의 transaction에서 기존 current를 해제한 뒤 새 report만 `CONFIRMED/current=true`가 된다.
- legacy `/api/stt` streaming session/file prototype, candidate TTL, report update/history/download는 별도 후속 작업이다.

## 다음 Shared Milestone

다음 shared milestone은 별도 AI/RAG 담당자가 T230의 embedding worker와 권한 필터된 pgvector retriever를 연결하는 작업이다. Backend owner는 기존 source contract와 embedding/vector 파일 경계를 변경하지 않는다.

## 검증 기준선

- AI: `cd ai && ./.venv/bin/python -m unittest discover -s tests`
- AI compile: `cd ai && ./.venv/bin/python -m compileall app`
- Backend: `cd backend && ./gradlew test`
- Frontend: `cd frontend && npm run build`
- Documents: `git diff --check`

## 기준 문서

- 실행 규칙: `AGENTS.md`, `AGENT.md`
- 제품 불변 원칙: `.specify/memory/constitution.md`
- 요구사항 라우팅: `requirements/INDEX.md`
- 현재 작업: `specs/001-meetingmind-core/tasks.md`
- 상세 구현/검증 기록: `specs/001-meetingmind-core/implement.md`
- API 계약: `specs/001-meetingmind-core/contracts/*`
- 데이터 관계: `specs/001-meetingmind-core/data-model.md`, `specs/001-meetingmind-core/erd.md`

## 개인 세션 이어받기

`.specify/memory/session-handoff.example.md`를
`.specify/memory/session-handoff.local.md`로 복사해 사용한다. local 파일은 Git에 포함하지 않는다.
