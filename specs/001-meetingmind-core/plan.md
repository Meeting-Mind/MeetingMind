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

## Data Model

- User: 사용자 식별, 이름, 이메일, 인증 공급자
- Space: 프로젝트 단위 컨테이너
- SpaceMember: Space별 멤버십과 역할
- Meeting: Space 하위 회의 회차
- MeetingParticipant: 회의별 접근 권한
- TranscriptSegment: 시간, 발화자, 텍스트, 회의 참조
- MeetingReport: 회의 요약, 결정사항, Action Item
- ProjectKnowledge: 공식 프로젝트 지식
- EmbeddingChunk: RAG 검색용 chunk와 vector

## Security and Permissions

- Backend API에서 Space/Meeting 접근 권한을 먼저 검증한다.
- AI 서버로 전달하는 컨텍스트는 Backend가 권한 필터링 후 구성하는 것을 목표로 한다.
- Project AI 구현 시 회의 데이터 retrieval 전에 MeetingParticipant 권한을 적용한다.
- LiveKit 토큰은 짧은 만료 시간을 유지한다.

## Parallel Work Plan

- Team Members: TBD
- Agents: TBD

| Workstream | Owner | Agent | Scope | Expected Files | Dependencies |
| --- | --- | --- | --- | --- | --- |
| Docs/Contracts | TBD | TBD | Open 질문 결정, API 계약, 데이터 모델, 작업 계획 갱신 | `specs/001-meetingmind-core/*` | - |
| Backend | TBD | TBD | Space/Meeting API 분리, 도메인 모델, 권한 검증 | `backend/**`, `specs/001-meetingmind-core/contracts/api.md`, `specs/001-meetingmind-core/data-model.md` | Q-001, Q-002, Docs/Contracts |
| Frontend | TBD | TBD | Project/Meeting 선택 상태, mock fallback 표시, 화면 연동 | `frontend/**` | API 계약 확정 |
| AI | TBD | TBD | Meeting AI 컨텍스트 조립 경로, 출처 메타데이터 | `ai/**`, `backend/**` | Backend 권한 필터 계약 |
| Data | TBD | TBD | PostgreSQL/pgvector 스키마 초안과 migration | `backend/**`, `specs/001-meetingmind-core/data-model.md` | Q-001, Q-002 |

## Conflict Boundaries

- Single-owner files:
  - `specs/001-meetingmind-core/contracts/api.md`: Docs/Contracts owner가 변경하고 Backend/Frontend/AI가 따른다.
  - `specs/001-meetingmind-core/data-model.md`: Docs/Contracts 또는 Data owner가 변경하고 Backend가 따른다.
  - migration 파일: Data owner가 순차 생성한다.
- Shared contracts:
  - API 계약, 권한 등급, Meeting AI response shape는 구현 전 먼저 합의한다.
- Do Not Edit Concurrently:
  - 같은 API endpoint 구현 파일
  - 같은 migration 파일
  - 같은 화면 route/component 파일
  - `specs/001-meetingmind-core/contracts/api.md`
  - `specs/001-meetingmind-core/data-model.md`

## Integration Order

1. Q-001 로그인 방식과 Q-002 회의 권한 등급을 결정한다.
2. API 계약과 데이터 모델을 확정한다.
3. Backend 도메인 모델과 권한 필터를 먼저 구현한다.
4. Frontend와 AI는 확정된 계약에 맞춰 병렬 구현한다.
5. Data migration은 Backend 모델과 맞춘 뒤 순차 통합한다.
6. Frontend, Backend, AI 권장 검증을 실행하고 통합 흐름을 수동 확인한다.

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
