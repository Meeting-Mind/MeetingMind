이 문서는 MeetingMind Core Prototype을 어떻게 구현할지 기술 계획, 병렬 작업 배정, 충돌 경계를 정리하기 위한 Markdown 문서이다.

# Implementation Plan: MeetingMind Core Prototype

## Current State

- Frontend는 React/Vite/TypeScript로 워크스페이스, 회의 대기, 라이브룸, Meeting AI, 프로젝트 개요, 팀원, Report Agent 화면을 제공한다.
- Backend는 Spring Boot 3/Java 21로 `/api/workspace` mock 응답과 `/api/livekit/token` 토큰 발급을 제공한다.
- AI 서버는 FastAPI로 `/api/meeting-ai/ask`를 제공하고 OpenAI Responses API를 직접 호출한다.
- 영속 DB, 인증, 실제 STT, pgvector RAG는 아직 없다.

## Target Architecture

- Frontend: mock fallback은 유지하되 API 계약과 실제 데이터 전환 지점을 분리한다.
- Backend: Space, Meeting, Membership, Report, Action Item, Knowledge API를 단계적으로 추가한다.
- AI: Meeting AI 컨텍스트 제한을 유지하고, 이후 retrieval 계층을 별도 모듈로 분리한다.
- Data: PostgreSQL + pgvector를 기본 영속 저장소로 설계하고, 파일성 원문/보고서는 S3 연계를 고려한다.

## Technical Decisions

| Decision | Choice | Reason | Alternatives |
| --- | --- | --- | --- |
| Frontend stack | React + Vite + TypeScript | 현재 코드와 일치하고 빠른 프로토타입 검증에 적합하다. | Next.js |
| Backend stack | Spring Boot 3 + Java 21 | 인증/권한/API 서버 확장에 적합하고 현재 코드와 일치한다. | Node/NestJS |
| AI service | FastAPI | AI API 연동과 Python RAG 생태계 확장에 적합하다. | Spring 내장 AI 호출 |
| Realtime meeting | LiveKit | 현재 토큰 발급 코드와 `livekit-client` 의존성이 존재한다. | WebRTC 직접 구현 |
| Vector search | PostgreSQL + pgvector | 기획서와 일치하고 관계형 권한 모델과 같이 운용하기 좋다. | Pinecone, OpenSearch |
| File storage | S3 | STT 원문/보고서/첨부 파일 분리에 적합하다. | DB BLOB |

## API Contracts

- `GET /api/workspace`
  - 현재: 전체 데모 화면 데이터를 한 번에 반환
  - 목표: Space/Meeting/Report API로 분리
- `POST /api/livekit/token`
  - request: `roomName`, `identity`, `name`
  - response: `serverUrl`, `token`, `roomName`, `identity`, `name`
- `POST /api/meeting-ai/ask`
  - request: `question`, `transcript`, `decisions`, `actions`
  - response: `answer`, `model`
- Common API rules
  - 공통 오류 응답은 `code`, `message`, `fieldErrors`, `traceId`를 포함한다.
  - API 날짜시간은 ISO-8601, transcript 위치는 `startMs`, `endMs` 밀리초를 사용한다.
  - 빈 배열은 `null` 대신 `[]`를 반환한다.
- Target transcript/speaker contracts
  - `GET /api/v1/meetings/{meetingId}/transcript`
  - `PATCH /api/v1/meetings/{meetingId}/speakers/{speakerId}`
  - 실제 STT 업로드/요약 재생성 API는 Future Draft로만 관리한다.

## Data Model

- User: 사용자 식별, 이름, 이메일, 인증 공급자
- Space: 프로젝트 단위 컨테이너
- SpaceMember: Space별 멤버십과 역할
- Meeting: Space 하위 회의 회차
- MeetingParticipant: 회의별 접근 권한
- MeetingSpeaker: 자동 발화자 구분 label과 사용자 지정 displayName
- TranscriptSegment: `startMs`, `endMs`, 발화자, 텍스트, 회의 참조
- MeetingReport: 회의 요약, 결정사항, Action Item
- ProjectKnowledge: 공식 프로젝트 지식
- EmbeddingChunk: RAG 검색용 chunk와 vector

## Security and Permissions

- Backend API에서 Space/Meeting 접근 권한을 먼저 검증한다.
- AI 서버로 전달하는 컨텍스트는 Backend가 권한 필터링 후 구성하는 것을 목표로 한다.
- Project AI 구현 시 회의 데이터 retrieval 전에 MeetingParticipant 권한을 적용한다.
- Transcript, summary, speaker 수정 API는 MeetingParticipant 권한 확인 후 처리한다.
- Speaker 이름 수정은 `host` 또는 `editor` 권한으로 제한한다.
- LiveKit 토큰은 짧은 만료 시간을 유지한다.

## Parallel Work Plan

- Team Members: 3명 예정. Auth/Login 담당 1명 확정, AI 담당 1명 기존 배정, 나머지 TBD
- Agents: 각 팀원은 코딩 에이전트 1개를 둔다. Auth/Login workstream은 Codex 사용

| Workstream | Owner | Agent | Scope | Expected Files | Dependencies |
| --- | --- | --- | --- | --- | --- |
| Docs/Contracts | TBD | TBD | Open 질문 결정, API 계약, 데이터 모델, 작업 계획 갱신 | `specs/001-meetingmind-core/*` | - |
| Auth/Login | 사용자(Auth 담당) | Codex | Google OAuth와 자체 회원가입/로그인, Backend 검증 기반 access/refresh token 계약/구현, Frontend 로그인 상태 연결, 보호 route 경계 정의 | `frontend/src/components/GoogleLoginModal.tsx`, `frontend/src/App.tsx`, future `frontend/src/auth/**`, future `backend/src/main/java/com/meetingmind/demo/auth/**`, `backend/src/main/java/com/meetingmind/demo/config/**`, `backend/src/main/resources/application.yml`, `specs/001-meetingmind-core/contracts/api.md`, `specs/001-meetingmind-core/clarify.md`, `specs/001-meetingmind-core/research.md`, `specs/001-meetingmind-core/tasks.md`, `specs/001-meetingmind-core/implement.md` | Q-001 decided. Auth API는 `/api/v1/auth/*`로 시작하며 Q-006 전체 API 결정과 분리 |
| Backend | TBD | TBD | Space/Meeting API 분리, 도메인 모델, 권한 검증 | `backend/**`, `specs/001-meetingmind-core/contracts/api.md`, `specs/001-meetingmind-core/data-model.md` | Auth/Login contract, Q-002, Docs/Contracts |
| Frontend | TBD | TBD | Project/Meeting 선택 상태, mock fallback 표시, 화면 연동 | `frontend/**` | API 계약 확정, Auth/Login guard 경계 |
| AI | 사용자 | Codex | 백엔드/프론트엔드 구현 없이 AI 서버에서 RAG chunk 형식, mock/in-memory retriever, 용어 설명, 회의 요약/보고서 생성, 회의별/프로젝트별 챗봇, 태스크 추출 prototype API를 준비한다. Backend 권한 필터 이후 컨텍스트 조립은 target architecture로 유지한다. | `ai/**`, `specs/001-meetingmind-core/*` | Backend 권한 필터, 실제 STT 저장 API, pgvector migration, Frontend 화면 연결은 후속 담당자 작업. 그 전까지 mock 또는 권한 필터링된 prototype context만 사용 |
| Data | TBD | TBD | PostgreSQL/pgvector 스키마 초안과 migration | `backend/**`, `specs/001-meetingmind-core/data-model.md` | Q-001, Q-002 |

## Conflict Boundaries

- Single-owner files:
  - Auth/Login owner: `frontend/src/components/GoogleLoginModal.tsx`, future `frontend/src/auth/**`, future `backend/src/main/java/com/meetingmind/demo/auth/**`
  - `specs/001-meetingmind-core/contracts/api.md`: Docs/Contracts owner가 변경하고 Backend/Frontend/AI가 따른다.
  - `specs/001-meetingmind-core/data-model.md`: Docs/Contracts 또는 Data owner가 변경하고 Backend가 따른다.
  - migration 파일: Data owner가 순차 생성한다.
- Shared contracts:
  - Auth API, token claim, refresh token session, API 계약, 권한 등급, Meeting AI response shape는 구현 전 먼저 합의한다.
- Do Not Edit Concurrently:
  - Auth/Login owner와 합의 없이 `GoogleLoginModal.tsx`, auth token 저장/전달 코드, backend auth package를 동시에 수정하지 않는다.
  - 같은 API endpoint 구현 파일
  - 같은 migration 파일
  - 같은 화면 route/component 파일
  - `specs/001-meetingmind-core/contracts/api.md`
  - `specs/001-meetingmind-core/data-model.md`

## Integration Order

1. Q-001 로그인 방식과 Auth API 경계를 확정한다.
2. Auth/Login owner가 확정된 Auth API, token 전달 방식, Frontend guard 경계에 맞춰 구현한다.
3. Q-002 회의 권한 등급을 결정한다.
4. API 계약과 데이터 모델을 확정한다.
5. 공통 오류 응답, Meeting status, transcript/speaker 계약을 확정한다.
6. Backend 도메인 모델과 권한 필터를 먼저 구현한다.
7. AI 담당은 실제 STT/DB를 기다리지 않고 `TranscriptSegment` 유사 mock 데이터에서 RAG chunk를 만드는 adapter와 in-memory retriever를 먼저 구현한다.
8. Frontend와 AI는 확정된 계약에 맞춰 병렬 구현한다.
9. Data migration은 Backend 모델과 맞춘 뒤 순차 통합한다.
10. Frontend, Backend, AI 권장 검증을 실행하고 통합 흐름을 수동 확인한다.

## Test Plan

- Frontend: `cd frontend && npm run build`
- Backend: `cd backend && mvn test`
- AI: `cd ai && python -m compileall app`
- Manual: 워크스페이스 홈, 회의 대기, 라이브룸, Meeting AI, Report Agent 화면 이동 확인

## Rollout Plan

1. 문서/스펙 기준선 확정
2. API 계약 분리와 mock fallback 정리
3. 인증/멤버십 모델 추가
4. 회의/보고서 영속화 추가
5. Meeting AI 서버 컨텍스트 조립을 backend 권한 필터 뒤로 이동
6. Project Knowledge와 권한 기반 RAG 추가
