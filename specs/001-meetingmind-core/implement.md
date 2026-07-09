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
- Frontend Auth 연결을 추가했다. 자체 로그인/회원가입과 Google credential exchange를 `/api/v1/auth/*`로 보내고, token pair는 `sessionStorage`에 저장한다.
- 랜딩(`/`) 외 앱 route를 보호 route로 감싸 비로그인 사용자는 로그인 모달로 유도하고, 로그인 성공 후 요청했던 route로 복귀하게 했다.
- Backend Auth runtime smoke 중 `AuthTokenService`, `GoogleJwtCredentialVerifier`의 테스트용 보조 생성자 때문에 Spring이 런타임 생성자를 선택하지 못하는 문제를 발견했다. 런타임 생성자에 `@Autowired`를 명시하고 context smoke test를 추가했다.
- Backend 빌드를 Maven에서 Gradle로 전환했다. 기본 Java 26 환경에서도 wrapper가 실행되도록 Gradle 9.6.1을 사용하고, 컴파일/테스트 toolchain은 Java 21로 유지한다.
- Google Sheets 요구사항 정의서를 `requirements/*` Markdown 기준선으로 분할했다.
- 기능/비기능 요구사항은 요약 카탈로그와 전체 우선순위 상세 문서로 분리했다. 상세 문서에는 P2를 포함해 인수 기준, 조건/권한, 실패/예외, 검증 기준 또는 측정 방법을 보존했다.
- 요구사항 읽기 전략을 `AGENTS.md`와 constitution에 반영했다. 구현자는 `requirements/INDEX.md`를 먼저 읽고 필요한 요구사항 문서만 추가로 읽는다.
- 용어집, 권한 매트릭스, 상태값 기준으로 core spec, plan, data-model, contracts, clarify, tasks, analyze를 갱신했다.
- 권한 role enum 표기는 용어집 기준인 `OWNER`/`ADMIN`/`MEMBER`, `HOST`/`EDITOR`/`VIEWER`로 정리했다.
- 정책, 성능지표, 용어집, 상태값 문서는 Google Sheets의 전체 컬럼을 보존하는 상세 스냅샷으로 보강했다.
- Q-002 회의 권한 등급은 `HOST`, `EDITOR`, `VIEWER`로 결정했고, 회의 게스트는 특정 회의의 `MeetingParticipant`로만 접근한다.
- Q-003 STT 원문 보존 기본값은 30일로 결정했다.
- Q-004 Project Knowledge는 SpaceMember가 조회하고 오너/관리자가 수정하며, 회의 게스트는 기본 접근할 수 없도록 정리했다.
- API 명세를 기능군별 파일로 분리하고, backend 전체 도메인 ERD 초안을 추가했다.
- API/ERD/Data Model 변경 시 관련 문서 영향 확인과 `implement.md` 로그 기록을 에이전트 지침에 추가했다.
- AI 담당 범위에서 분리 API/ERD 기준선을 재검토하고, RAG scope/unsupported/sourceId/candidate 안전 검증을 보강했다.

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
| 사용자 | Codex | T104, T087-T088 | 요구사항 기준선 반영 후 AI/RAG 영향도 점검, RAG safety 검증, AI workstream closeout |
| 사용자 | Codex | T078-T079 | RAG chunk/embeddingText 형식 문서화와 AI 서버 RAG 타입 경계 추가 |
| 사용자(Auth 담당) | Codex | T024 | Google OAuth 단독, 자체 계정/JWT, Google OAuth + 자체 계정 + access/refresh token 병행안 비교와 결정 정리 |
| 사용자(Auth 담당) | Codex | T089-T096 | 로그인/인증 기반 구축 작업 경계, Auth API 계약, Backend 검증, Frontend auth 상태, 보호 route guard 계획 |
| 사용자(Auth 담당) | Codex | T089 | 현재 Frontend Google 로그인 모달과 Backend auth/security 부재 상태 조사 |
| 사용자(Auth 담당) | Codex | T090 | `/api/v1/auth/*` Auth API, User/AuthIdentity/AuthSession 모델, token storage/protected route 계약 문서화 |
| 사용자 | Codex | T107-T109 | 기능군별 API 명세 분리, backend 전체 도메인 ERD 초안, API/ERD 변경 로그 지침 보강 |

## Files Changed

- `AGENTS.md`: Codex 등 크로스툴 공통 지침, 7개 개념 계층, Git 협업 지침
- `AGENT.md`: 사용자가 직접 작성한 코딩 에이전트 행동 규칙
- `CLAUDE.md`: Claude Code 호환용 포인터
- `.specify/memory/constitution.md`: 프로젝트 불변 원칙
- `.specify/templates/*`: 반복 작업용 템플릿
- `.specify/skills/qa-checklist.md`: 도구 중립 QA 체크리스트
- `specs/001-meetingmind-core/*`: MeetingMind 핵심 프로토타입 스펙 세트
- `requirements/*`: Google Sheets 요구사항 정의서의 로컬 Markdown 기준선
- `specs/001-meetingmind-core/contracts/README.md`: 기능군별 API 명세 라우팅과 변경 규칙
- `specs/001-meetingmind-core/contracts/common.md`: 공통 API 규칙, 오류, role/status/source shape
- `specs/001-meetingmind-core/contracts/auth-api.md`: Auth API 명세 초안
- `specs/001-meetingmind-core/contracts/space-api.md`: Space, dashboard, calendar, member, owner transfer API 명세 초안
- `specs/001-meetingmind-core/contracts/meeting-api.md`: Meeting, ACL, transcript, report API 명세 초안
- `specs/001-meetingmind-core/contracts/kanban-api.md`: Kanban, task card, task candidate API 명세 초안
- `specs/001-meetingmind-core/contracts/knowledge-api.md`: Project Knowledge와 Domain Term 관리 API 명세 초안
- `specs/001-meetingmind-core/contracts/ai-api.md`: Meeting AI, Project AI, report/task candidate, term API 명세 초안
- `specs/001-meetingmind-core/contracts/live-stt-api.md`: LiveKit, meeting room, STT/dialogue API 명세 초안
- `specs/001-meetingmind-core/erd.md`: backend 전체 도메인 ERD 초안
- `ai/app/main.py`: `POST /api/meeting-ai/explain-term` endpoint와 Domain Dictionary 우선 응답, AI fallback 추가
- `ai/app/rag.py`: RAG chunk/source/search request/result 타입과 retriever protocol 추가
- `backend/src/main/java/com/meetingmind/demo/service/LiveKitTokenService.java`: 안정적인 단위 테스트를 위해 clock/config provider 주입 경계 추가
- `backend/build.gradle`, `backend/settings.gradle`, `backend/gradlew*`, `backend/gradle/wrapper/*`: Backend 빌드를 Maven에서 Gradle로 전환
- `backend/src/main/java/com/meetingmind/demo/auth/**`: in-memory Auth store, PBKDF2 password hash, HMAC access token, refresh token hash/revoke, Google ID token verifier, Auth controller/error response 추가
- `backend/src/test/java/com/meetingmind/demo/service/LiveKitTokenServiceTest.java`: LiveKit JWT claim/signature 성공 케이스와 설정 누락 실패 케이스 테스트
- `backend/src/test/java/com/meetingmind/demo/auth/AuthServiceTest.java`: signup/me, 중복 이메일, 비밀번호 실패, refresh rotation, Google identity 연결 테스트
- `backend/src/test/java/com/meetingmind/demo/MeetingMindApplicationTest.java`: Spring bean wiring context smoke 테스트 추가
- `frontend/src/auth/session.ts`: Auth API client, `sessionStorage` 저장/조회, `Authorization` header helper 추가
- `frontend/src/components/GoogleLoginModal.tsx`: Google 로그인 모달을 자체 로그인/회원가입과 Google Backend exchange가 가능한 auth modal로 확장
- `frontend/src/App.tsx`: auth session 상태, protected route, `/api/workspace` Authorization header 전달 추가
- `frontend/src/styles/app.css`: auth modal form/tab style 추가
- `ai/tests/test_meeting_ai.py`: glossary 우선순위, 근거 없음 응답, transcript source 제한, RAG source mapping 테스트

## Conflict Notes

- 제품 코드 변경 범위는 AI prototype, Backend Auth, Frontend Auth로 확장되었다. Auth 외 `backend/**`, `frontend/**` 변경은 각 workstream owner와 합의한다.
- Auth/Login workstream은 `GoogleLoginModal.tsx`, future `frontend/src/auth/**`, future `backend/**/auth/**`를 우선 소유한다. Frontend/Backend 담당자는 auth token 저장/전달, auth endpoint, backend auth package를 수정하기 전에 Auth owner와 합의한다.
- 현재 `.idea/`는 작업 범위 밖 개인 IDE 설정으로 Git 미추적 상태를 유지한다. 커밋 전 포함하지 않는다.

## Integration Result

- 공통 병렬 작업 원칙은 `AGENTS.md`에 추가했다.
- 기능별 병렬 계획은 `plan.md`와 `plan-template.md`에 추가했다.
- 작업별 owner/agent/dependency/files 관리는 `tasks.md`와 `tasks-template.md`에 추가했다.
- milestone과 task granularity 기준은 `AGENTS.md`, `tasks.md`, `tasks-template.md`에 추가했다.
- 실제 배정/충돌/통합 기록은 `implement.md`와 `implement-template.md`에 추가했다.
- 공통 API 규칙, 오류 응답, Meeting status, transcript/speaker 계약은 `contracts/common.md`, `contracts/meeting-api.md`, `contracts/live-stt-api.md`, `data-model.md`, `plan.md`, `clarify.md`, `tasks.md`에 반영했다.
- 실제 구현 착수는 `tasks.md`의 T024-T106 상세 task 기준으로 진행한다.
- AI 담당 workstream은 `tasks.md`의 T070-T088로 분리했다. T070-T072와 T078-T088은 완료했고, `backend/**`와 `frontend/**` 구현은 다른 담당자 배정 전까지 `TBD`로 유지한다.
- AI prototype API 계약은 `contracts/ai-api.md`에 정리했다. 범위는 용어 설명, 회의 요약/보고서 생성, 회의별 챗봇, 프로젝트별 챗봇, 회의 종료 태스크 후보 추출이다.
- 용어 설명 prototype은 `pgvector` 같은 Domain Dictionary 항목을 로컬 응답으로 먼저 처리하고, dictionary에 없지만 transcript 근거가 있는 용어는 AI fallback으로 설명한다.
- RAG 기반 작업은 `tasks.md`의 M011/T078-T088로 세분화했다. T078-T088은 완료했고, 실제 STT 저장 API/DB schema/pgvector migration은 후속 담당자 작업으로 남긴다.
- Auth/Login 기반 작업은 `tasks.md`의 M012/T089-T096으로 세분화했다. T089-T093, T095-T096은 완료했고, LiveKit token을 회의 접근 권한과 연결하는 T094는 T040 이후로 남긴다.
- 요구사항 기준선 반영 작업은 `tasks.md`의 M013/T097-T106으로 세분화했다. T097-T101은 완료했고, backend/frontend/ai/data 영향도 점검은 T102-T105로 남긴다.
- T102-T105는 도메인 구현과 실제 코드 영향도 점검이므로 각 영역 팀원이 담당한다. Codex는 문서 기준선, 상세 요구사항 스냅샷, 용어/enum 정합성까지만 정리했다.
- API/ERD 기준선 작업은 `tasks.md`의 M014/T107-T110으로 세분화했다. T107-T109는 완료했고, 각 기능 owner의 상세 리뷰와 충돌 점검은 T110으로 남긴다.

## Contract and ERD Change Log

- 2026-07-09: 단일 `contracts/api.md` 기준선을 기능군별 명세로 분리했다. 신규 구현 기준은 `contracts/README.md`의 라우팅을 따른다.
- 2026-07-09: `erd.md`를 추가해 Auth, Space, Meeting, STT, Report, Kanban, AI/RAG, Audit 관계 초안을 Mermaid ERD로 작성했다.
- 2026-07-09: `AGENTS.md`에 API/ERD/Data Model 변경 시 문서 영향 확인과 `implement.md` 로그 기록 규칙을 추가했다.
- 2026-07-09: `.specify/templates/api-contract-template.md`와 `contracts/README.md`에 endpoint 표준 섹션 규칙을 추가하고, 분리 API 문서에 Status/Auth/Data Scope/Validation/Errors/Audit/Requirement Trace/Notes 구조를 적용했다.
- 2026-07-09: `contracts/api.md`를 legacy snapshot으로 명확히 하고, `plan.md`, `tasks.md`, `analyze.md`의 API 문서 참조를 분리 contract 파일 기준으로 갱신했다. 검증은 문서 포맷 중심으로 수행한다.
- 2026-07-09: API 문서의 `Requirement Trace`를 실제 `requirements/functional-requirements.md` ID와 대조해 수정하고, 누락된 `knowledge-api.md`를 추가했다. `erd.md`에는 ProjectKnowledge/DomainTerm 상태 필드와 주요 unique/index/nullable 제약을 보강했다.
- 2026-07-09: Invitation은 `SPACE_INVITATION`/`MEETING_INVITATION`으로 분리, MeetingReport는 회의당 current confirmed 1개, ProjectKnowledge embedding은 비동기 재생성으로 결정했다. 관련 결정은 `clarify.md` D-010~D-012와 API/ERD/data-model에 반영했다.
- 2026-07-09: Q-005 보고서 파일 포맷은 Markdown 우선으로 결정했다. PDF/DOCX export는 후속 옵션으로 둔다.
- 2026-07-09: 자체 회원가입 비밀번호 정책을 `POL-PW-01` 수준으로 올렸다. Backend signup은 최소 8자와 영대문자/영소문자/숫자/특수문자 중 3종 이상 포함을 서버에서 검증한다.
- 2026-07-09: Backend auth/권한 후속 구현 순서는 `T039/T040` Space/Meeting 접근 검증 service, `T094` LiveKit token 권한 연동, Auth store DB 영속화 순서로 확정했다.
- 2026-07-09: GitHub Actions CI 기준선을 추가했다. PR/push에서 Backend test, Frontend build, AI compile/unit test를 분리 job으로 실행한다.
- 2026-07-09: SpaceMember 제거 시 같은 Space의 member MeetingParticipant를 `REVOKED`로 전환하는 정책을 확정했다. `MeetingParticipant.accessStatus` canonical 값은 `ACTIVE`, `REVOKED`로 status-values, data-model, ERD, contracts에 반영했다.
- 2026-07-09: HOST 일시 퇴장, 회의 종료, 마지막 HOST 회수/강등/삭제 금지 정책을 확정했다. `ADMIN`은 서비스 전체 운영자가 아니라 SpaceRole의 프로젝트 관리자임을 용어집과 결정 로그에 명시했다.
- 2026-07-09: 회의 삭제 권한은 기본 `OWNER`/`HOST` 전용으로 확정하고, `ADMIN` 삭제는 명시적 예외 정책이 있을 때만 허용하도록 정책/권한/API 계약에 반영했다.
- 2026-07-09: `AuthIdentity.provider` 표기를 `local`, `google`로 통일했다. ERD의 로컬 인증 provider 제약도 같은 기준으로 맞췄다.

## Current Auth Workstream Notes

- 현재 Frontend에는 `frontend/src/components/GoogleLoginModal.tsx`가 있으며, 자체 로그인/회원가입과 Google Backend exchange를 처리한다.
- `frontend/src/App.tsx`는 auth session 상태와 protected route를 관리하고, `/api/workspace` 호출에 `Authorization` header를 전달한다.
- 기존 mock fallback은 유지되지만 로그인 상태는 Backend auth 응답 기준으로 관리한다.
- 현재 Backend Auth 구현은 새 외부 dependency 없이 Java 표준 crypto/Jackson/Spring MVC 기반으로 작성했다. Spring Security는 아직 도입하지 않았다.
- 현재 Backend Auth package는 `backend/src/main/java/com/meetingmind/demo/auth/**`다.
- 확정 구현 방향대로 Google OAuth와 자체 회원가입/로그인을 모두 지원하고, Backend가 access token과 refresh token, user profile을 반환한다.
- Frontend에서 Google credential을 decode하는 코드는 표시용으로만 취급하고 인증 신뢰 경계로 사용하지 않는다.
- Auth API는 기존 prototype API와 충돌하지 않도록 `/api/v1/auth/*`로 시작한다.
- Frontend는 access token과 refresh token을 `sessionStorage`에 저장한다.
- 공개 route는 랜딩(`/`)만 두고, `/spaces`, `/project-overview`, `/live-meeting`, `/live-room`, `/meeting-ai`, `/report-agent`, `/team-members`는 로그인 필요 대상으로 둔다.
- 로그인 성공 후에는 비로그인 상태에서 요청했던 보호 route로 복귀한다.
- Backend Auth runtime 환경변수는 `MEETINGMIND_JWT_SECRET` 또는 `AUTH_JWT_SECRET`, Google 검증용 `GOOGLE_CLIENT_ID` 또는 `VITE_GOOGLE_CLIENT_ID`를 사용한다.
- 현재 Auth 저장소는 prototype용 in-memory store다. 서버 재시작 시 사용자, identity, refresh session은 사라지며, DB 영속화는 Data/Backend 후속 작업이다.
- LiveKit token을 인증 사용자와 회의 접근 권한에 연결하는 T094는 T040 회의 접근 검증 계층 구현 전까지 구현하지 않는다.

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
- 2026-07-09: `contracts/ai-api.md`, `contracts/knowledge-api.md`, `erd.md`, `data-model.md` 기준으로 AI owner review를 수행했다. 현재 AI prototype은 Meeting AI 단일 meeting scope, Project AI 공식 지식/허용 meeting summary 분리, 근거 없음 `unsupported=true`, registered glossary LLM 미호출 원칙과 맞는다.
- 2026-07-09: T104 영향도 점검 결과, token budget 축소 정책과 AI/API observability log는 아직 구현되어 있지 않다. 이는 Backend 권한 필터, 실제 RAG 저장소, 운영 로깅이 들어오는 후속 milestone에서 처리한다.
- 2026-07-09: RAG safety unittest를 추가했다. Meeting scope가 다른 meeting/projectKnowledge chunk를 제외하고, Project scope가 허용되지 않은 meeting chunk를 제외하며, 근거 없는 Meeting AI/task extraction은 OpenAI를 호출하지 않는다.
- 2026-07-09: Report action item도 저장 전 산출물 원칙에 맞게 `confirmationState=candidate`로 정규화하도록 수정했다. LLM이 임의 sourceId를 반환해도 제공된 source 목록에 없는 값은 제거된다.

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
10. T087-T088: RAG safety와 최종 검증 완료

## Git Status Notes

- 요구사항 기준선 반영 변경은 PR #8의 `agent/requirements-docs-baseline` 브랜치에 커밋되어 원격 push되었다.
- 개인 IDE 설정과 에이전트 산출물 디렉터리인 `.idea/`, `output/`, `tmp/`는 Git에 올리지 않고 루트 `.gitignore`에서 제외한다.
- `c15ca74 feat: add backend auth prototype` 이후 Frontend Auth 연결, Backend Auth runtime wiring fix, context smoke test, Auth 검증 문서 갱신을 후속 Auth 변경으로 정리했다.

## Verification

- Passed: `cd ai && python3 -m compileall app`
- Passed: `git diff --check`
- Passed: `cd ai && .venv/bin/python -c "from app.main import ExplainTermRequest, explain_term; ..."`로 Domain Dictionary 우선 응답 확인
- Passed: `curl -fsS http://127.0.0.1:8000/health`
- Passed: `curl -fsS -X POST http://127.0.0.1:8000/api/meeting-ai/explain-term ...`
- Passed: `cd backend && ./gradlew test`
- Passed: `cd backend && ./gradlew build`
- Historical Maven verification before Gradle conversion: `cd backend && mvn -Dtest=LiveKitTokenServiceTest test`, `cd backend && mvn test`
- Passed: `cd ai && python3 -m unittest discover -s tests`
- Passed: `cd ai && python3 -m compileall app tests`
- Passed: `cd ai && python3 -m compileall app tests` after AI RAG safety tests
- Passed: `cd ai && ./.venv/bin/python -m unittest discover -s tests`, 9 tests
- Passed: `cd frontend && npm run build`
- Passed: `cd frontend && npm run build` after Frontend Auth route guard changes
- Passed: `cd backend && ./gradlew test` after Gradle conversion, total 8 backend tests
- Passed: `cd frontend && npm run build` after Auth runtime fix and docs update candidate
- Passed: `cd ai && python3 -m unittest discover -s tests`, 4 tests
- Passed: `cd ai && python3 -m compileall app tests`
- Passed: `git diff --check`
- Passed: `cd backend && ./gradlew test` after `POL-PW-01` password policy and CI baseline changes
- Passed: `cd frontend && npm run build` after CI baseline changes
- Passed: `cd ai && python3 -m compileall app tests` after CI baseline changes
- Passed: `cd ai && python3 -m unittest discover -s tests`, 4 tests, after CI baseline changes
- Passed: `git diff --check` after CI baseline changes
- Passed: local runtime smoke with `MEETINGMIND_JWT_SECRET=dev-test-secret GOOGLE_CLIENT_ID=dev-google-client mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18080` before Gradle conversion
- Passed: `curl -fsS http://127.0.0.1:18080/api/workspace`
- Passed: `curl -fsS http://127.0.0.1:18080/api/v1/auth/signup -H 'Content-Type: application/json' -d '{"email":"api-smoke-18080@meetingmind.ai","password":"password-123","displayName":"API Smoke"}'`
- Not run: Browser automation verification. `agent-browser` CLI and Playwright packages are not available in this environment; adding a new browser test library was avoided because existing frontend test framework is not present.
- Not run: `cd ai && python -m compileall app`는 이 환경에 `python` 명령이 없어 `python3`로 대체했다.
- Note: 첫 `mvn -Dtest=LiveKitTokenServiceTest test` 실행은 Maven이 `~/.m2`에 Surefire provider를 쓸 권한이 없어 실패했고, 승인 후 재실행해 통과했다.
- Note: `npm ci` 후 `npm audit`이 moderate 1건, high 1건을 보고했다. `npm audit fix --force`는 breaking change 가능성이 있어 실행하지 않았다.
- Note: `8080`, `8081`, `5173`, `5174`는 기존 로컬 프로세스가 사용 중이었다. Auth runtime smoke는 충돌을 피하려고 Backend `18080`, Frontend `5176`으로 실행했다.

## Remaining Work

- `clarify.md`의 Open 질문 결정
- Backend Auth 영속화: 현재 in-memory Auth store를 DB 기반 User/AuthIdentity/AuthSession 저장소로 전환
- LiveKit token auth 연결(T094): T040 회의 접근 검증 계층 구현 후 인증 사용자와 회의 권한을 확인하도록 전환
- 요구사항 기준선 영향도 점검: T102-T105에서 backend/frontend/ai/data가 새 용어, 권한, 상태값, 성능/토큰 기준과 충돌하는지 확인
- mock API 분리
- Target API base URL 결정
- 실제 STT 파일 업로드 방식 결정
- Meeting AI 권한 필터링 경로 강화
- Project AI 실제 DB/pgvector RAG 연결과 Backend 권한 선필터 통합
- Frontend 연결: Live Room 용어 설명 UI, Meeting/Project AI source 표시, Report Agent 연결은 Frontend 담당 TBD
- Frontend/Backend 연결: AI prototype endpoint를 권한 필터 이후 Backend route와 화면에 연결
