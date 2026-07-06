이 문서는 MeetingMind Core Prototype의 미결정 질문과 확정된 해석을 기록하기 위한 Markdown 문서이다.

# Clarification Log: MeetingMind Core Prototype

## Questions

| ID | Priority | Question | Why It Matters | Status | Decision |
| --- | --- | --- | --- | --- | --- |
| Q-001 | High | 로그인은 Google OAuth 단독으로 시작할까, 자체 계정/JWT를 병행할까? | Backend 보안 구조와 Frontend 인증 흐름을 결정한다. | Open | |
| Q-002 | High | 회의 권한 등급은 host/editor/participant/viewer로 충분한가? | API 권한 모델과 UI 제어 범위를 결정한다. | Open | |
| Q-003 | Medium | STT 원문 기본 보존 기간은 7일, 30일, 영구 중 무엇인가? | 저장 비용, 개인정보, 삭제 작업 설계를 결정한다. | Open | |
| Q-004 | Medium | Project Knowledge는 누가 공식 승인하고 최신화하는가? | Project AI가 공식 지식과 회의 기록을 구분하는 기준이 된다. | Open | |
| Q-005 | Low | 보고서 파일 포맷은 Markdown, HTML, PDF, DOCX 중 무엇을 우선할까? | Report Agent 저장/다운로드 구현 방향을 결정한다. | Open | |
| Q-006 | Medium | Target API Base URL은 `/api/v1`로 고정할까, 현재 prototype 경로와 병행할까? | Frontend client 구성과 Backend route migration 순서를 결정한다. | Open | |
| Q-007 | Medium | 실제 오디오 업로드는 multipart 직접 업로드로 시작할까, presigned URL 방식을 우선할까? | 대용량 파일 처리, S3 연동, 보안 경계를 결정한다. | Open | |

## Blocking Decisions

- Q-001은 Backend 인증/인가 모델과 Frontend 로그인 흐름 구현 전에 결정해야 한다.
- Q-002는 `MeetingParticipant.role`, 권한 필터, 회의 UI 제어 구현 전에 결정해야 한다.
- Q-006은 Target API route를 실제 구현하기 전에 결정해야 한다.
- Q-007은 실제 STT 파일 업로드 구현 전에 결정해야 한다.

## Current Assumptions

- 프로토타입 단계에서는 로그인/인가를 mock 상태로 표현한다.
- 실제 보안 구현 전에는 AI 컨텍스트에 민감 데이터를 넣지 않는다.
- Meeting AI를 먼저 안정화한 뒤 Project AI RAG를 구현한다.
- 실제 STT 업로드 API는 현재 Core Prototype의 확정 계약이 아니라 Future Draft로 관리한다.

## Decisions

- D-001: 현재 AI 담당 범위는 `ai/**`와 프론트엔드 AI 화면 연결로 제한한다. `backend/**` 권한 필터, 컨텍스트 조립, 저장 API 구현은 다른 담당자가 맡을 때까지 `TBD`로 둔다.
- D-002: 문서 원칙상 최종 구조는 Backend가 권한 필터를 적용한 뒤 AI 서버에 컨텍스트를 전달하는 방식이다. 다만 AI 담당 prototype 작업은 백엔드 구현 전까지 mock 또는 이미 권한 필터링된 데모 컨텍스트만 사용한다.
- D-003: AI prototype API는 우선 AI 서버 직접 호출 계약으로 정의한다. Backend route, 저장, 권한 필터 구현은 후속 담당자 작업이므로 현재 계약에는 already-filtered context 전제를 명시한다.
- D-004: 실제 STT 저장 API, DB schema, pgvector migration은 후속 담당자 작업을 기다린다. AI 담당은 그 전까지 `TranscriptSegment` 유사 mock 데이터에서 `RagChunk`를 생성하는 adapter 경계와 in-memory retriever를 먼저 구현한다.
