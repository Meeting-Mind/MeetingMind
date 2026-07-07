이 문서는 MeetingMind Core Prototype의 구현 결과, 작업 배정, 충돌 처리, 남은 작업을 기록하기 위한 Markdown 문서이다.

# Implementation Log: MeetingMind Core Prototype

## Scope Implemented

- Spec Kit 기반 문서 계층을 추가했다.
- 프로젝트 헌법, 공통 에이전트 지침, 기능 스펙, 기술 계획, 데이터 모델, API 계약, 작업 목록, 일관성 분석 문서를 작성했다.
- specs 변경 동반 갱신 규칙, 누락 템플릿, 작업 완료 기준, clarification 우선순위, analysis 추적 상태를 보완했다.
- Git 작업 절차, staging, commit, pull, branch, push 규칙을 구체화했다.
- 팀/에이전트 병렬 작업 계획, 충돌 경계, owner/agent 기반 tasks 형식을 추가했다.
- milestone 기반 task 작성 규칙과 에이전트 친화 작업 단위 기준을 추가했다.
- API_SPEC 초안에서 공통 API 규칙, Meeting status, 오류 응답, transcript/speaker 계약 후보를 MeetingMind 기준으로 선별 반영했다.
- 기존 umbrella task T010-T018을 즉시 배정 가능한 상세 task T024-T069로 분해했다.
- AI 담당 workstream을 T070-T077로 분리하고, 용어 설명 prototype의 AI 서버 endpoint를 추가했다.
- Auth/Login 담당 workstream을 분리하고, Q-001 인증 방식 선택지와 최종 결정을 정리했다.
- Q-001을 Google OAuth와 자체 회원가입/로그인 병행, access/refresh token 발급, `/api/v1/auth/*`, `sessionStorage`, 랜딩 외 보호 route로 확정했다.
- LiveKit token 발급 로직에 JUnit 단위 테스트를 추가하고, AI 용어 설명/RAG source mapping에 Python 표준 unittest를 추가했다.
- Backend Auth API를 `/api/v1/auth/*`로 추가했다. 자체 signup/login, Google credential login, refresh, me, logout endpoint와 access/refresh token 발급을 구현했다.

## Work Allocation

| Owner | Agent | Tasks | Scope |
| --- | --- | --- | --- |
| Codex | Codex | T021 | 병렬 작업 지침, 템플릿, core plan/tasks/implement/handoff 문서 보완 |
| Codex | Codex | T022 | milestone 기반 task 작성 규칙과 에이전트 친화 작업 단위 기준 보완 |
| Codex | Codex | T023 | API 계약, 데이터 모델, 계획, clarification 문서 보완 |
| Codex | Codex | T024-T069 planning | milestone별 상세 구현 task 분해 |
| 사용자 | Codex | T070-T077 | 백엔드/프론트엔드 구현 없이 AI 서버 중심으로 용어 설명, 요약/보고서 생성, 챗봇, 태스크 추출 prototype 작업 착수 |
| 사용자 | Codex | T070-T071 | 현재 AI 코드 경계 기록과 AI prototype API 계약 문서화 |
| 사용자 | Codex | T072 | 회의 중 transcript 용어 설명 prototype 구현 |
| 사용자 | Codex | T078-T088 | STT/DB 구현 전 RAG chunk 형식, in-memory retriever, RAG 기반 AI 기능 전환 작업 |
| 사용자 | Codex | T078-T079 | RAG chunk/embeddingText 형식 문서화와 AI 서버 RAG 타입 경계 추가 |
| 사용자(Auth 담당) | Codex | T024 | Google OAuth 단독, 자체 계정/JWT, Google OAuth + 자체 계정 + access/refresh token 병행안 비교와 결정 정리 |
| 사용자(Auth 담당) | Codex | T089-T096 | 로그인/인증 기반 구축 작업 경계, Auth API 계약, Backend 검증, Frontend auth 상태, 보호 route guard 계획 |
| 사용자(Auth 담당) | Codex | T089 | 현재 Frontend Google 로그인 모달과 Backend auth/security 부재 상태 조사 |
| 사용자(Auth 담당) | Codex | T090 | `/api/v1/auth/*` Auth API, User/AuthIdentity/RefreshTokenSession 모델, token storage/protected route 계약 문서화 |

## Files Changed

- `AGENTS.md`: Codex 등 크로스툴 공통 지침, 7개 개념 계층, Git 협업 지침
- `AGENT.md`: 사용자가 직접 작성한 코딩 에이전트 행동 규칙
- `CLAUDE.md`: Claude Code 호환용 포인터
- `.specify/memory/constitution.md`: 프로젝트 불변 원칙
- `.specify/templates/*`: 반복 작업용 템플릿
- `.specify/skills/qa-checklist.md`: 도구 중립 QA 체크리스트
- `specs/001-meetingmind-core/*`: MeetingMind 핵심 프로토타입 스펙 세트
- `ai/app/main.py`: `POST /api/meeting-ai/explain-term` endpoint와 Domain Dictionary 우선 응답, AI fallback 추가
- `ai/app/rag.py`: RAG chunk/source/search request/result 타입과 retriever protocol 추가
- `backend/src/main/java/com/meetingmind/demo/service/LiveKitTokenService.java`: 안정적인 단위 테스트를 위해 clock/config provider 주입 경계 추가
- `backend/src/main/java/com/meetingmind/demo/auth/**`: in-memory Auth store, PBKDF2 password hash, HMAC access token, refresh token hash/revoke, Google ID token verifier, Auth controller/error response 추가
- `backend/src/test/java/com/meetingmind/demo/service/LiveKitTokenServiceTest.java`: LiveKit JWT claim/signature 성공 케이스와 설정 누락 실패 케이스 테스트
- `backend/src/test/java/com/meetingmind/demo/auth/AuthServiceTest.java`: signup/me, 중복 이메일, 비밀번호 실패, refresh rotation, Google identity 연결 테스트
- `ai/tests/test_meeting_ai.py`: glossary 우선순위, 근거 없음 응답, transcript source 제한, RAG source mapping 테스트

## Conflict Notes

- 제품 코드 변경 범위는 AI 서버로 제한했다. `backend/**`와 `frontend/**`는 변경하지 않는다.
- Auth/Login workstream은 `GoogleLoginModal.tsx`, future `frontend/src/auth/**`, future `backend/**/auth/**`를 우선 소유한다. Frontend/Backend 담당자는 auth token 저장/전달, auth endpoint, backend auth package를 수정하기 전에 Auth owner와 합의한다.
- 현재 문서 기준선 전체가 미추적 상태이므로 커밋 전 포함 범위를 확인해야 한다.

## Integration Result

- 공통 병렬 작업 원칙은 `AGENTS.md`에 추가했다.
- 기능별 병렬 계획은 `plan.md`와 `plan-template.md`에 추가했다.
- 작업별 owner/agent/dependency/files 관리는 `tasks.md`와 `tasks-template.md`에 추가했다.
- milestone과 task granularity 기준은 `AGENTS.md`, `tasks.md`, `tasks-template.md`에 추가했다.
- 실제 배정/충돌/통합 기록은 `implement.md`와 `implement-template.md`에 추가했다.
- 공통 API 규칙, 오류 응답, Meeting status, transcript/speaker 계약은 `contracts/api.md`, `data-model.md`, `plan.md`, `clarify.md`, `tasks.md`에 반영했다.
- 실제 구현 착수는 `tasks.md`의 T024-T096 상세 task 기준으로 진행한다.
- AI 담당 workstream은 `tasks.md`의 T070-T077로 분리했다. T070-T072를 완료 상태로 두고, `backend/**`와 `frontend/**` 구현은 다른 담당자 배정 전까지 `TBD`로 유지한다.
- AI prototype API 계약은 `contracts/api.md`에 추가했다. 범위는 용어 설명, 회의 요약/보고서 생성, 회의별 챗봇, 프로젝트별 챗봇, 회의 종료 태스크 후보 추출이다.
- 용어 설명 prototype은 `pgvector` 같은 Domain Dictionary 항목을 로컬 응답으로 먼저 처리하고, dictionary에 없지만 transcript 근거가 있는 용어는 AI fallback으로 설명한다.
- RAG 기반 작업은 `tasks.md`의 M011/T078-T088로 세분화했다. T078-T079를 완료하고 T080을 시작 상태로 두었으며, 실제 STT 저장 API/DB schema/pgvector migration은 후속 담당자 작업으로 남긴다.
- Auth/Login 기반 작업은 `tasks.md`의 M012/T089-T096으로 세분화했다. T024, T089, T090을 완료 상태로 두고, 실제 Backend Auth 구현은 T091부터 진행한다.

## Current Auth Workstream Notes

- 현재 Frontend에는 `frontend/src/components/GoogleLoginModal.tsx`가 있으며, Google Identity Services script를 로드하고 credential payload를 decode해 `onSuccess`로 사용자 표시 정보를 넘긴다.
- 현재 `GoogleLoginModal.tsx`는 import 사용처가 없어 실제 route guard와 연결되지 않았다.
- 현재 `frontend/src/App.tsx`는 `/api/workspace`를 호출하고 실패 시 mock data를 유지하며, auth state 또는 `Authorization` header 전달 경계는 없다.
- 현재 Backend Auth 구현은 새 외부 dependency 없이 Java 표준 crypto/Jackson/Spring MVC 기반으로 작성했다. Spring Security는 아직 도입하지 않았다.
- 현재 Backend Auth package는 `backend/src/main/java/com/meetingmind/demo/auth/**`다.
- 확정 구현 방향대로 Google OAuth와 자체 회원가입/로그인을 모두 지원하고, Backend가 access token과 refresh token, user profile을 반환한다.
- Frontend에서 Google credential을 decode하는 코드는 표시용으로만 취급하고 인증 신뢰 경계로 사용하지 않는다.
- Auth API는 기존 prototype API와 충돌하지 않도록 `/api/v1/auth/*`로 시작한다.
- Frontend는 access token과 refresh token을 `sessionStorage`에 저장한다.
- 공개 route는 랜딩(`/`)만 두고, `/spaces`, `/project-overview`, `/live-meeting`, `/live-room`, `/meeting-ai`, `/report-agent`, `/team-members`는 로그인 필요 대상으로 둔다.
- Backend Auth runtime 환경변수는 `MEETINGMIND_JWT_SECRET` 또는 `AUTH_JWT_SECRET`, Google 검증용 `GOOGLE_CLIENT_ID` 또는 `VITE_GOOGLE_CLIENT_ID`를 사용한다.
- 현재 Auth 저장소는 prototype용 in-memory store다. 서버 재시작 시 사용자, identity, refresh session은 사라지며, DB 영속화는 Data/Backend 후속 작업이다.

## Current AI Workstream Notes

- AI 서버 현재 진입점은 `ai/app/main.py`의 `/api/meeting-ai/ask`이며, question, transcript, decisions, actions를 받아 OpenAI Responses API를 직접 호출한다.
- 프론트 화면 연결 지점은 읽기 전용으로 확인했다. 실제 `frontend/**` 구현은 Frontend 담당자 작업으로 남긴다.
- 현재 Meeting AI 화면은 AI 서버를 직접 호출하고, Project AI 화면은 같은 `/api/meeting-ai/ask`를 재사용한다. 이 분리는 AI 서버 endpoint/contract부터 정리한다.
- Live Room의 STT 표시와 Report Agent의 로컬 편집 흐름은 현재 프론트 구현을 유지하고, AI 담당 브랜치에서는 수정하지 않는다.
- 백엔드 권한 필터, Meeting/Project context 조립, AI 응답 저장, 태스크 저장 API는 아직 구현되지 않았고 현재 AI 담당 범위에서 제외한다.
- Prototype AI 계약은 `POST /api/meeting-ai/explain-term`, `POST /api/meeting-ai/generate-report`, `POST /api/meeting-ai/chat`, `POST /api/project-ai/chat`, `POST /api/meeting-ai/extract-tasks`로 정의했다.
- `POST /api/meeting-ai/explain-term` 구현을 추가했다. glossary 일치 시 `local-glossary` 모델 라벨로 즉시 응답하고, transcript 근거가 있으면 OpenAI fallback을 호출한다.
- RAG chunk 계획은 STT 원천 데이터 `TranscriptSegment`와 임베딩 검색 단위 `RagChunk/EmbeddingChunk`를 분리한다. 짧은 발화는 여러 segment window로 묶고, `embeddingText`에는 회의명, scope, sourceType, 시간, 발화자, 내용을 포함한다.
- `ai/app/rag.py`에 `RagChunk`, `RagSource`, `RagSearchRequest`, `RagSearchResult`, `RagRetriever` 경계를 추가했다.
- `ai/app/rag.py`에 mock transcript, decision, action item, project knowledge를 `RagChunk`로 변환하는 builder를 추가했다. 짧은 STT 발화는 3-8개 범위의 window chunk로 묶고, `sourceSegmentIds`, 발화자, 시간 범위, `sourceType`, `embeddingText` metadata를 유지한다.
- 2026-07-06: `cd ai && python3 -m compileall app` 검증과 builder smoke test를 통과했다. 다음 작업은 pgvector 전환 전 in-memory retriever와 meeting/project scope 필터를 구현하는 T081이다.
- `ai/app/rag.py`에 `InMemoryRagRetriever`를 추가했다. 검색은 projectId, scope, meetingId 또는 allowedMeetingIds, sourceTypes 필터를 먼저 적용하고, prototype 단계에서는 정규화 token match 점수로 상위 chunk를 반환한다.
- 2026-07-06: retriever smoke test에서 meeting scope가 단일 meetingId만 반환하고, project scope가 `projectKnowledge`와 `allowedMeetingIds`에 포함된 meeting chunk만 검색하는 것을 확인했다. 다음 작업은 회의 중 용어 설명 endpoint를 retriever 기반으로 전환하는 T082이다.
- `POST /api/meeting-ai/explain-term`을 RAG retriever 기반 source 검색으로 전환했다. glossary exact match는 계속 우선 처리하고, transcript fallback은 `TranscriptRow -> TranscriptSegment -> RagChunk -> InMemoryRagRetriever` 경로로 source를 구성한다.
- 2026-07-06: `ai/.venv/bin/python -m compileall app`와 용어 설명 RAG source smoke test를 통과했다. 다음 작업은 회의별 챗봇을 meeting scope RAG로 구현하는 T083이다.
- `POST /api/meeting-ai/chat`을 추가했다. 요청으로 받은 단일 회의 transcript, decision, action item만 RAG chunk로 변환하고 `scope=meeting`/`meetingId` 필터로 검색한 source를 OpenAI에 전달한다.
- 2026-07-06: `ai/.venv/bin/python -m compileall app`와 meeting chat RAG source smoke test를 통과했다. 검색 결과가 없을 때는 OpenAI 호출 없이 `unsupported=true`, `model=context-only`로 응답한다. 다음 작업은 프로젝트별 챗봇을 project scope RAG로 구현하는 T084이다.
- `POST /api/project-ai/chat`을 추가했다. 요청으로 받은 `projectKnowledge`는 공식 프로젝트 지식 chunk로, `meetings[].summary`는 접근 허용된 회의 요약 chunk로 변환하고 `scope=project`로 검색한다.
- 2026-07-06: `ai/.venv/bin/python -m compileall app`와 project chat RAG source smoke test를 통과했다. 검색 결과가 없을 때는 OpenAI 호출 없이 `unsupported=true`, `model=context-only`로 응답한다. 다음 작업은 회의 요약/보고서 생성 prototype인 T085이다.
- `POST /api/meeting-ai/generate-report`를 추가했다. 요청으로 받은 회의 transcript, decision, action item을 RAG chunk/source 구조로 변환하고, OpenAI에는 sourceId가 포함된 회의 근거를 전달해 summary, decisions, actionItems, markdown 초안을 JSON으로 받는다.
- 2026-07-06: `ai/.venv/bin/python -m compileall app`와 report source/JSON parse smoke test를 통과했다. 근거가 없을 때는 OpenAI 호출 없이 `unsupported=true`, `model=context-only`로 응답한다. 다음 작업은 회의 종료 태스크 후보 추출 prototype인 T086이다.
- 2026-07-06: 실제 OpenAI 보고서 생성 호출은 `certifi` CA bundle 적용 후 API endpoint까지 도달했으나, 현재 로컬 OpenAI key가 유효하지 않아 401 invalid key로 실패했다. AI 서버는 루트 `.env`도 읽고 `OPEN_AI_KEY` alias도 `OPENAI_API_KEY`로 인식하도록 보완했으며, 유효한 key 교체 후 실제 생성 결과를 다시 확인한다.
- `POST /api/meeting-ai/extract-tasks`를 추가했다. transcript와 summary를 source-aware RAG chunk로 변환하고, OpenAI에는 참석자 목록과 sourceId가 포함된 회의 근거를 전달해 task candidate JSON을 받는다.
- 2026-07-06: `ai/.venv/bin/python -m compileall app`와 task extraction source/JSON parse smoke test를 통과했다. 모든 태스크 후보는 저장 전 상태인 `confirmationState=candidate`로 정규화한다. 다음 작업은 RAG scope와 컨텍스트 밖 질문 방어를 검증하는 T087이다.

## AI RAG Task Priority

1. T078: RAG chunk와 embeddingText 형식 정의
2. T079: AI 서버 내부 RAG 타입과 retriever 경계 추가
3. T080: mock transcript/decision/action/projectKnowledge chunk builder 구현 완료
4. T081: in-memory retriever와 meeting/project scope 필터 구현 완료
5. T082: 회의 중 용어 설명을 retriever 기반으로 전환 완료
6. T083: 회의별 챗봇 RAG scope 구현 완료
7. T084: 프로젝트별 챗봇 RAG scope 구현 완료
8. T085: 회의 요약/보고서 생성 prototype 구현 완료
9. T086: 회의 종료 태스크 후보 추출 prototype 구현 완료
10. T087-T088: RAG safety와 최종 검증

## Git Status Notes

- 문서 기준선은 `codex/docs-agent-collaboration-workflow` 브랜치에 커밋되어 원격 push되었다.
- 현재 PDF 공유 산출물인 `output/`, `tmp/`는 Git 미추적 상태다.
- 현재 Auth 문서/테스트 변경과 AI prototype 착수 변경은 아직 커밋하지 않은 로컬 변경이다.

## Verification

- Passed: `cd ai && python3 -m compileall app`
- Passed: `git diff --check`
- Passed: `cd ai && .venv/bin/python -c "from app.main import ExplainTermRequest, explain_term; ..."`로 Domain Dictionary 우선 응답 확인
- Passed: `curl -fsS http://127.0.0.1:8000/health`
- Passed: `curl -fsS -X POST http://127.0.0.1:8000/api/meeting-ai/explain-term ...`
- Passed: `cd backend && mvn -Dtest=LiveKitTokenServiceTest test`
- Passed: `cd backend && mvn test`
- Passed: `cd backend && mvn test`로 AuthServiceTest 포함 총 7개 backend test 통과
- Passed: `cd ai && python3 -m unittest discover -s tests`
- Passed: `cd ai && python3 -m compileall app tests`
- Passed: `cd frontend && npm run build`
- Not run: `cd ai && python -m compileall app`는 이 환경에 `python` 명령이 없어 `python3`로 대체했다.
- Note: 첫 `mvn -Dtest=LiveKitTokenServiceTest test` 실행은 Maven이 `~/.m2`에 Surefire provider를 쓸 권한이 없어 실패했고, 승인 후 재실행해 통과했다.
- Note: `npm ci` 후 `npm audit`이 moderate 1건, high 1건을 보고했다. `npm audit fix --force`는 breaking change 가능성이 있어 실행하지 않았다.

## Remaining Work

- `clarify.md`의 Open 질문 결정
- Backend Auth 영속화: 현재 in-memory Auth store를 DB 기반 User/AuthIdentity/RefreshTokenSession 저장소로 전환
- Frontend Auth 구현(T092-T093): 로그인/회원가입 UI 흐름, `sessionStorage` token pair 저장, 랜딩 외 route 보호
- mock API 분리
- Target API base URL 결정
- 실제 STT 파일 업로드 방식 결정
- Meeting AI 권한 필터링 경로 강화
- Project AI RAG 설계와 구현
- AI prototype 구현: 요약/보고서 생성, 회의별/프로젝트별 챗봇 분리, 회의 종료 태스크 후보 추출 API
- Frontend 연결: Live Room 용어 설명 UI, Meeting/Project AI source 표시, Report Agent 연결은 Frontend 담당 TBD
- RAG prototype 구현: `ai/app/rag.py` 추가, mock retriever 연결, sources/citations UI 표시
