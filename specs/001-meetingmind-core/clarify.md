이 문서는 MeetingMind Core Prototype의 미결정 질문과 확정된 해석을 기록하기 위한 Markdown 문서이다.

# Clarification Log: MeetingMind Core Prototype

## Questions

| ID | Priority | Question | Why It Matters | Status | Decision |
| --- | --- | --- | --- | --- | --- |
| Q-001 | High | 로그인은 Google OAuth 단독으로 시작할까, 자체 계정/JWT를 병행할까? | Backend 보안 구조와 Frontend 인증 흐름을 결정한다. | Decided | Google OAuth와 자체 회원가입/로그인을 모두 지원한다. Backend는 access token과 refresh token을 발급하고, Frontend는 둘 다 `sessionStorage`에 저장한다. |
| Q-002 | High | 회의 권한 등급은 어떤 값으로 정할까? | API 권한 모델과 UI 제어 범위를 결정한다. | Decided | MeetingRole은 `HOST`, `EDITOR`, `VIEWER`를 기본값으로 한다. `participant`는 role 값으로 쓰지 않고, 회의 게스트는 SpaceRole이 아니라 특정 회의의 MeetingParticipant로 등록한다. |
| Q-003 | Medium | STT 원문 기본 보존 기간은 7일, 30일, 영구 중 무엇인가? | 저장 비용, 개인정보, 삭제 작업 설계를 결정한다. | Decided | STT 원문 보존 선택지는 7일/30일/영구이며 기본값은 30일이다. 음성 원본은 기본 장기 보관하지 않는다. |
| Q-004 | Medium | Project Knowledge는 누가 공식 승인하고 최신화하는가? | Project AI가 공식 지식과 회의 기록을 구분하는 기준이 된다. | Decided | Project Knowledge는 SpaceMember가 조회하고 오너/관리자가 수정한다. 회의 게스트는 기본 접근할 수 없다. |
| Q-005 | Low | 보고서 파일 포맷은 Markdown, HTML, PDF, DOCX 중 무엇을 우선할까? | Report Agent 저장/다운로드 구현 방향을 결정한다. | Decided | Markdown을 우선한다. PDF/DOCX export는 후속 옵션으로 둔다. |
| Q-006 | Medium | Target API Base URL은 `/api/v1`로 고정할까, 현재 prototype 경로와 병행할까? | Frontend client 구성과 Backend route migration 순서를 결정한다. | Open | |
| Q-007 | Medium | 실제 오디오 업로드는 multipart 직접 업로드로 시작할까, presigned URL 방식을 우선할까? | 대용량 파일 처리, S3 연동, 보안 경계를 결정한다. | Open | |

## Blocking Decisions

- Q-006은 Target API route를 실제 구현하기 전에 결정해야 한다. 단, Auth API는 충돌 최소화를 위해 `/api/v1/auth/*`로 먼저 시작한다.
- Q-007은 실제 STT 파일 업로드 구현 전에 결정해야 한다.

## Q-001 Authentication Options

| Option | Summary | Pros | Cons | Impact |
| --- | --- | --- | --- | --- |
| Google OAuth only | Frontend Google Identity Services 결과를 로그인 상태의 중심으로 둔다. | 빠르게 시작할 수 있고 비밀번호 저장이 없다. | Backend가 앱 고유 권한 token을 갖지 못해 Space/Meeting 권한, 만료, 감사 로그 확장이 약하다. Frontend에서 credential을 decode하는 것은 표시용일 뿐 신뢰 경계가 될 수 없다. | `frontend/src/components/GoogleLoginModal.tsx`, auth guard |
| Own account/JWT only | 이메일/비밀번호 또는 자체 가입과 JWT를 직접 운영한다. | Google 계정 없이도 사용할 수 있고 token 정책을 완전히 통제한다. | 비밀번호 저장, 가입/재설정, 보안 운영 범위가 커져 prototype 목적에 비해 무겁다. | Backend security, User credential model, Frontend signup/login UI |
| Google OAuth + own account + access/refresh token | Google OAuth와 자체 이메일/비밀번호 계정을 모두 지원하고 Backend가 access token과 refresh token을 발급한다. | 사용자는 Google 또는 자체 계정으로 진입할 수 있고, Backend가 Space/Meeting 권한 판단에 쓸 앱 내부 subject를 안정적으로 가진다. refresh token으로 세션 연장이 가능하다. | 비밀번호 hash 저장, refresh token 폐기, token rotation, Google token 검증을 모두 다뤄야 해서 구현 범위가 커진다. | `contracts/auth-api.md`, `contracts/common.md`, `data-model.md`, `frontend/src/components/GoogleLoginModal.tsx`, `frontend/src/App.tsx`, future `frontend/src/auth/**`, future `backend/**/auth/**`, `application.yml` |

### Final Direction

- Prototype 구현은 Google OAuth와 자체 회원가입/로그인을 모두 지원한다.
- Frontend의 Google credential decode는 사용자 표시용으로만 사용하고, 실제 인증은 Backend 검증 결과만 신뢰한다.
- Access token은 `Authorization: Bearer {accessToken}`로 전달한다.
- Backend는 access token과 refresh token을 모두 발급한다.
- Frontend는 access token과 refresh token을 모두 `sessionStorage`에 저장한다.
- Auth API는 충돌 최소화를 위해 `/api/v1/auth/*`로 새로 만든다. 기존 prototype API는 당분간 유지한다.
- 랜딩(`/`)만 공개하고, `/spaces`, `/project-overview`, `/live-meeting`, `/live-room`, `/meeting-ai`, `/report-agent`, `/team-members`는 로그인 필요 대상으로 둔다.
- LiveKit token 발급은 후속 단계에서 인증된 사용자와 `MeetingParticipant` 권한 확인 뒤 허용한다.

## Current Assumptions

- 프로토타입 단계에서는 로그인/인가를 mock 상태로 표현했으나, Auth workstream에서는 Backend 검증 기반 로그인으로 전환한다.
- 실제 보안 구현 전에는 AI 컨텍스트에 민감 데이터를 넣지 않는다.
- Meeting AI를 먼저 안정화한 뒤 Project AI RAG를 구현한다.
- 실제 STT 업로드 API는 현재 Core Prototype의 확정 계약이 아니라 Future Draft로 관리한다.

## Decisions

- D-001: 현재 AI 담당 범위는 `ai/**`와 AI 관련 문서로 제한한다. `backend/**` 권한 필터, 컨텍스트 조립, 저장 API 구현과 `frontend/**` 화면 연결은 다른 담당자가 맡을 때까지 `TBD`로 둔다.
- D-002: 문서 원칙상 최종 구조는 Backend가 권한 필터를 적용한 뒤 AI 서버에 컨텍스트를 전달하는 방식이다. 다만 AI 담당 prototype 작업은 백엔드 구현 전까지 mock 또는 이미 권한 필터링된 데모 컨텍스트만 사용한다.
- D-003: AI prototype API는 우선 AI 서버 직접 호출 계약으로 정의한다. Backend route, 저장, 권한 필터 구현은 후속 담당자 작업이므로 현재 계약에는 already-filtered context 전제를 명시한다.
- D-004: 실제 STT 저장 API, DB schema, pgvector migration은 후속 담당자 작업을 기다린다. AI 담당은 그 전까지 `TranscriptSegment` 유사 mock 데이터에서 `RagChunk`를 생성하는 adapter 경계와 in-memory retriever를 먼저 구현한다.
- D-005: Auth는 Google OAuth와 자체 회원가입/로그인을 모두 지원한다. Auth API는 `/api/v1/auth/*`로 시작하고, Backend가 access token과 refresh token을 발급하며, Frontend는 두 token을 `sessionStorage`에 저장한다. 랜딩(`/`)만 공개 route로 둔다.
- D-006: 요구사항 기준선은 `requirements/*` Markdown으로 관리한다. 작업자는 `requirements/INDEX.md`를 먼저 읽고 관련 요구사항 문서만 추가로 읽는다.
- D-007: MeetingRole은 `HOST`, `EDITOR`, `VIEWER`를 기본값으로 한다. `participant`는 MeetingRole 값으로 쓰지 않고, 일반 참석자는 `VIEWER` 또는 별도 `participantType=member`로 표현한다.
- D-008: 회의 게스트는 특정 회의의 `MeetingParticipant`로 등록되며 Space 전체 권한, Project Knowledge, Project AI 권한을 기본으로 갖지 않는다.
- D-009: Meeting status는 `SCHEDULED`, `IN_PROGRESS`, `ENDED`, `CANCELED`를 기준으로 한다. 전사/보고서 후처리는 `Transcript.status`, `MeetingReport.status`로 분리한다.
- D-010: Space 초대와 Meeting 초대는 `SPACE_INVITATION`, `MEETING_INVITATION`으로 분리한다. Space 초대 수락은 `SpaceMember`를 만들고, Meeting 초대 수락은 `MeetingParticipant`만 만든다.
- D-011: 회의당 현재 공식 회의록은 `status=CONFIRMED`와 `isCurrent=true`를 만족하는 report 최대 1개로 제한한다. 과거 버전은 version history로 보존한다.
- D-012: ProjectKnowledge 변경 후 embedding 재생성은 비동기로 처리한다. 기존 chunk는 유지하고 새 chunk가 `COMPLETED`가 되면 교체한다.
- D-013: 보고서 파일 포맷은 Markdown을 우선한다. Report Agent 저장 모델과 우선 export는 Markdown 기준으로 맞추고, PDF/DOCX는 후속 export 옵션으로 둔다.
- D-014: 비밀번호 정책은 `POL-PW-01` 수준으로 적용한다. 자체 회원가입 비밀번호는 최소 8자이며 영대문자, 영소문자, 숫자, 특수문자 중 3종 이상을 포함해야 한다.
- D-015: Backend auth/권한 후속 구현 순서는 `T039/T040` Space/Meeting 접근 검증 service, `T094` LiveKit token 권한 연동, Auth store DB 영속화 순서로 진행한다. 이유는 LiveKit/AI/회의 데이터 접근이 먼저 MeetingParticipant 권한 판단을 필요로 하기 때문이다.
- D-016: SpaceMember 제거 시 해당 Space의 `participantType=member`인 active MeetingParticipant는 모두 `accessStatus=REVOKED`로 전환한다. 회의 guest participant는 SpaceMember 제거 API 대상이 아니므로 이 정책으로 회수하지 않는다.
- D-017: AI 회의록 생성 결과는 재조회와 확정을 위해 `MeetingReport.CANDIDATE`로 임시 저장한다. candidate는 기본 공식 회의록 조회와 Project AI source에서 제외하고, `status=CANDIDATE`를 명시한 조회 또는 생성 응답에서만 노출한다. AI가 `unsupported=true`를 반환하면 저장하지 않는다.
- D-017: `MeetingParticipant.accessStatus`는 `ACTIVE`, `REVOKED`를 canonical 값으로 사용한다. `ACTIVE`만 회의 접근 권한으로 인정하고 `REVOKED`는 조회, 수정, LiveKit token, AI context 접근을 모두 차단한다.
- D-018: HOST의 회의방 일시 퇴장은 허용하며 role/accessStatus를 유지한다. HOST가 회의를 종료하면 Meeting status를 `ENDED`로 전환한다. 마지막 active HOST의 강등, 접근 회수, participant 제거는 거부하며, 마지막 HOST를 없애려면 다른 참여자를 먼저 HOST로 승격해야 한다.
- D-019: `ADMIN`은 서비스 전체 운영자나 프로그램 관리자가 아니라 특정 Space 안에서 오너가 위임한 프로젝트 관리자 역할이다. 서비스 전체 운영자 역할은 현재 Core Prototype 범위 밖이다.
- D-020: 회의 삭제 권한은 기본 `OWNER` 또는 해당 회의 `HOST` 전용이다. `ADMIN`은 회의 생성/참여자 관리/수정 override를 가질 수 있지만 삭제 권한은 기본 포함하지 않는다. `ADMIN` 삭제는 명시적 예외 정책이 문서화된 경우에만 허용한다.
- D-021: `AuthIdentity.provider` 값은 `local`, `google`로 통일한다. 자체 이메일/비밀번호 계정은 `provider=local`이며 `passwordHash`는 `provider=local`일 때만 required다.
