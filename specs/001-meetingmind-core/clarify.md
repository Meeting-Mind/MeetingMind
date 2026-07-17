이 문서는 MeetingMind Core Prototype의 미결정 질문과 확정된 해석을 기록하기 위한 Markdown 문서이다.

# Clarification Log: MeetingMind Core Prototype

## Questions

| ID | Priority | Question | Why It Matters | Status | Decision |
| --- | --- | --- | --- | --- | --- |
| Q-001 | High | 로그인은 Google OAuth 단독으로 시작할까, 자체 계정/JWT를 병행할까? | Backend 보안 구조와 Frontend 인증 흐름을 결정한다. | Superseded for target | Core Prototype은 Google OAuth와 자체 계정, Backend token 발급과 Frontend `sessionStorage`를 구현했다. 목표 브라우저 인증은 `../002-bff-auth-msa/clarify.md` D-001~D-007의 BFF 서버 세션으로 대체한다. |
| Q-002 | High | 회의 권한 등급은 어떤 값으로 정할까? | API 권한 모델과 UI 제어 범위를 결정한다. | Decided | MeetingRole은 `HOST`, `EDITOR`, `VIEWER`를 기본값으로 한다. `participant`는 role 값으로 쓰지 않고, 회의 게스트는 SpaceRole이 아니라 특정 회의의 MeetingParticipant로 등록한다. |
| Q-003 | Medium | STT 원문 기본 보존 기간은 7일, 30일, 영구 중 무엇인가? | 저장 비용, 개인정보, 삭제 작업 설계를 결정한다. | Decided | STT 원문 보존 선택지는 7일/30일/영구이며 기본값은 30일이다. 음성 원본은 기본 장기 보관하지 않는다. |
| Q-004 | Medium | Project Knowledge는 누가 공식 승인하고 최신화하는가? | Project AI가 공식 지식과 회의 기록을 구분하는 기준이 된다. | Decided | Project Knowledge는 SpaceMember가 조회하고 오너/관리자가 수정한다. 회의 게스트는 기본 접근할 수 없다. |
| Q-005 | Low | 보고서 파일 포맷은 Markdown, HTML, PDF, DOCX 중 무엇을 우선할까? | Report Agent 저장/다운로드 구현 방향을 결정한다. | Decided | Markdown을 우선한다. PDF/DOCX export는 후속 옵션으로 둔다. |
| Q-006 | Medium | Target API Base URL은 `/api/v1`로 고정할까, 현재 prototype 경로와 병행할까? | Frontend client 구성과 Backend route migration 순서를 결정한다. | Open | |
| Q-007 | Medium | 실제 오디오 업로드는 multipart 직접 업로드로 시작할까, presigned URL 방식을 우선할까? | 대용량 파일 처리, S3 연동, 보안 경계를 결정한다. | Open | |
| Q-008 | Medium | AI 회의록 candidate는 생성 후 얼마 동안 확정할 수 있는가? | FR-RPT-02~03의 만료 candidate 거부와 정리 작업 기준을 결정한다. | Open | |
| Q-009 | Medium | AI 태스크 candidate는 생성 후 얼마 동안 확정할 수 있는가? | FR-TASK-02의 만료 candidate 거부와 정리 작업 기준을 결정한다. | Open | |
| Q-010 | High | pgvector embedding model과 차원 수는 무엇으로 고정할까? | `vector(n)` 타입과 HNSW/IVFFlat index는 차원 수가 확정되어야 안전하게 생성할 수 있다. | Decided | MVP는 `text-embedding-3-small`, 1536차원, cosine exact search를 사용한다. 권한 선필터 후 후보가 5,000개를 넘거나 검색 p95가 1초를 지속적으로 초과하면 HNSW를 검토하고 IVFFlat은 기본 선택에서 제외한다. |
| Q-011 | Medium | 회의 채팅 첨부파일을 어떤 방식으로 검색할까? | 텍스트·PDF·이미지를 같은 vector schema에 섞으면 추출 방식과 모델 차원이 불명확해진다. | Decided | MVP는 TXT, Markdown, 텍스트 추출 가능한 PDF만 추출 텍스트를 `text-embedding-3-small`로 임베딩한다. 이미지 파일, 이미지 전용 PDF, visual embedding과 Vision 기반 답변은 확장 범위로 둔다. |

## Blocking Decisions

- Q-006은 Target API route를 실제 구현하기 전에 결정해야 한다. 단, Auth API는 충돌 최소화를 위해 `/api/v1/auth/*`로 먼저 시작한다.
- Q-007은 실제 STT 파일 업로드 구현 전에 결정해야 한다.
- Q-008은 candidate 만료 검증과 정리 작업 구현 전에 결정해야 한다. 상태·권한·current 전이는 먼저 구현할 수 있다.
- Q-009는 TaskCandidate 만료 검증과 정리 작업 구현 전에 결정해야 한다. 상태 전이와 중복 확정 방지는 먼저 구현할 수 있다.
- Q-010은 D-032로 결정했다. 실제 embedding 생성 전 `vector(1536)` forward migration과 한국어 검색 평가 기준을 적용한다.
- Q-011은 D-037로 결정했다. 첨부파일 데이터/API 계약 전까지 이미지 처리와 visual vector schema를 추가하지 않는다.

## Q-001 Authentication Options

| Option | Summary | Pros | Cons | Impact |
| --- | --- | --- | --- | --- |
| Google OAuth only | Frontend Google Identity Services 결과를 로그인 상태의 중심으로 둔다. | 빠르게 시작할 수 있고 비밀번호 저장이 없다. | Backend가 앱 고유 권한 token을 갖지 못해 Space/Meeting 권한, 만료, 감사 로그 확장이 약하다. Frontend에서 credential을 decode하는 것은 표시용일 뿐 신뢰 경계가 될 수 없다. | `frontend/src/components/GoogleLoginModal.tsx`, auth guard |
| Own account/JWT only | 이메일/비밀번호 또는 자체 가입과 JWT를 직접 운영한다. | Google 계정 없이도 사용할 수 있고 token 정책을 완전히 통제한다. | 비밀번호 저장, 가입/재설정, 보안 운영 범위가 커져 prototype 목적에 비해 무겁다. | Backend security, User credential model, Frontend signup/login UI |
| Google OAuth + own account + access/refresh token | Google OAuth와 자체 이메일/비밀번호 계정을 모두 지원하고 Backend가 access token과 refresh token을 발급한다. | 사용자는 Google 또는 자체 계정으로 진입할 수 있고, Backend가 Space/Meeting 권한 판단에 쓸 앱 내부 subject를 안정적으로 가진다. refresh token으로 세션 연장이 가능하다. | 비밀번호 hash 저장, refresh token 폐기, token rotation, Google token 검증을 모두 다뤄야 해서 구현 범위가 커진다. | `contracts/auth-api.md`, `contracts/common.md`, `data-model.md`, `frontend/src/components/GoogleLoginModal.tsx`, `frontend/src/App.tsx`, future `frontend/src/auth/**`, future `backend/**/auth/**`, `application.yml` |

### Prototype Compatibility Direction

- Prototype 구현은 Google OAuth와 자체 회원가입/로그인을 모두 지원한다.
- Frontend의 Google credential decode는 사용자 표시용으로만 사용하고, 실제 인증은 Backend 검증 결과만 신뢰한다.
- Access token은 `Authorization: Bearer {accessToken}`로 전달한다.
- Backend는 access token과 refresh token을 모두 발급한다.
- Frontend는 access token과 refresh token을 모두 `sessionStorage`에 저장한다.
- Auth API는 충돌 최소화를 위해 `/api/v1/auth/*`로 새로 만든다. 기존 prototype API는 당분간 유지한다.
- 랜딩(`/`)만 공개하고, `/spaces`, `/project-overview`, `/live-meeting`, `/live-room`, `/meeting-ai`, `/report-agent`, `/team-members`는 로그인 필요 대상으로 둔다.
- LiveKit token 발급은 후속 단계에서 인증된 사용자와 `MeetingParticipant` 권한 확인 뒤 허용한다.

이 방향은 현재 Core 구현과 BFF Phase 1 compatibility adapter를 설명한다. 신규 Frontend/BFF/Auth 구현은 `../002-bff-auth-msa/contracts/*`와 데이터 모델을 우선한다.

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
- D-005: Core Prototype Auth는 Google OAuth와 자체 회원가입/로그인을 모두 지원한다. `/api/v1/auth/*`에서 Backend가 access/refresh를 발급하고 현재 Frontend가 `sessionStorage`에 저장한다. 이 결정은 legacy compatibility로 유지한다.
- D-006: 요구사항 기준선은 `requirements/*` Markdown으로 관리한다. 작업자는 `requirements/INDEX.md`를 먼저 읽고 관련 요구사항 문서만 추가로 읽는다.
- D-007: MeetingRole은 `HOST`, `EDITOR`, `VIEWER`를 기본값으로 한다. `participant`는 MeetingRole 값으로 쓰지 않고, 일반 참석자는 `VIEWER` 또는 별도 `participantType=member`로 표현한다.
- D-008: 회의 게스트는 특정 회의의 `MeetingParticipant`로 등록되며 Space 전체 권한, Project Knowledge, Project AI 권한을 기본으로 갖지 않는다.
- D-009: Meeting status는 `SCHEDULED`, `IN_PROGRESS`, `ENDED`, `CANCELED`를 기준으로 한다. 전사/보고서 후처리는 `Transcript.status`, `MeetingReport.status`로 분리한다.
- D-010: Space 초대와 회의 참가 흐름을 분리한다. `SPACE_INVITATION` 수락은 `SpaceMember`를 만들고, 사용자-facing 회의 참여는 URL/코드 기반 `MEETING_JOIN_REQUEST`를 HOST가 승인한 뒤 `MeetingParticipant`만 만든다. 기존 `MEETING_INVITATION` 계약은 현재 기본 흐름에서 제외한다.
- D-011: 회의당 현재 공식 회의록은 `status=CONFIRMED`와 `isCurrent=true`를 만족하는 report 최대 1개로 제한한다. 과거 버전은 version history로 보존한다.
- D-012: ProjectKnowledge 변경 후 embedding 재생성은 비동기로 처리한다. 기존 chunk는 유지하고 새 chunk가 `COMPLETED`가 되면 교체한다.
- D-013: 보고서 파일 포맷은 Markdown을 우선한다. Report Agent 저장 모델과 우선 export는 Markdown 기준으로 맞추고, PDF/DOCX는 후속 export 옵션으로 둔다.
- D-014: 비밀번호 정책은 `POL-PW-01` 수준으로 적용한다. 자체 회원가입 비밀번호는 최소 8자이며 영대문자, 영소문자, 숫자, 특수문자 중 3종 이상을 포함해야 한다.
- D-015: Backend auth/권한 후속 구현 순서는 `T039/T040` Space/Meeting 접근 검증 service, `T094` LiveKit token 권한 연동, Auth store DB 영속화 순서로 진행한다. 이유는 LiveKit/AI/회의 데이터 접근이 먼저 MeetingParticipant 권한 판단을 필요로 하기 때문이다.
- D-016: SpaceMember 제거 시 해당 Space의 `participantType=member`인 active MeetingParticipant는 `participantType=guest`로 전환한다. SpaceMember 제거는 프로젝트 전체 접근권만 제거하며, 특정 회의 접근 차단은 MeetingParticipant revoke로 별도 처리한다.
- D-017: `MeetingParticipant.accessStatus`는 `ACTIVE`, `REVOKED`를 canonical 값으로 사용한다. `ACTIVE`만 회의 접근 권한으로 인정하고 `REVOKED`는 조회, 수정, LiveKit token, AI context 접근을 모두 차단한다.
- D-018: HOST의 회의방 일시 퇴장은 허용하며 role/accessStatus를 유지한다. HOST가 회의를 종료하면 Meeting status를 `ENDED`로 전환한다. 마지막 active HOST의 강등, 접근 회수, participant 제거는 거부하며, 마지막 HOST를 없애려면 다른 참여자를 먼저 HOST로 승격해야 한다.
- D-019: `ADMIN`은 서비스 전체 운영자나 프로그램 관리자가 아니라 특정 Space 안에서 오너가 위임한 프로젝트 관리자 역할이다. 서비스 전체 운영자 역할은 현재 Core Prototype 범위 밖이다.
- D-020: 회의 삭제 권한은 기본 `OWNER` 또는 해당 회의 `HOST` 전용이다. `ADMIN`은 회의 생성/참여자 관리/수정 override를 가질 수 있지만 삭제 권한은 기본 포함하지 않는다. `ADMIN` 삭제는 명시적 예외 정책이 문서화된 경우에만 허용한다.
- D-021: `AuthIdentity.provider` 값은 `local`, `google`로 통일한다. 자체 이메일/비밀번호 계정은 `provider=local`이며 `passwordHash`는 `provider=local`일 때만 required다.
- D-022: AI 회의록 생성 결과는 재조회와 확정을 위해 `MeetingReport.CANDIDATE`로 임시 저장한다. candidate는 기본 공식 회의록 조회와 Project AI source에서 제외하고, `status=CANDIDATE`를 명시한 조회 또는 생성 응답에서만 노출한다. AI가 `unsupported=true`를 반환하면 저장하지 않는다.
- D-023: 태스크 추출은 `OWNER`/`ADMIN` 또는 해당 회의의 active `HOST`/`EDITOR`가 실행한다. 후보 조회는 active 회의 접근 권한이 필요하고, TaskCard 확정은 회의 편집 권한과 active `SpaceMember`를 모두 요구한다. AI가 `unsupported=true`를 반환하면 후보를 저장하지 않으며 후보당 TaskCard는 최대 하나만 생성한다.
- D-024: 회의 생성 시 추측하기 어려운 `joinCode`를 발급한다. 인증 사용자는 회의 URL 또는 코드만으로 `PENDING` 참가 신청을 만들 수 있고, active `HOST`가 승인하면 기본 `VIEWER` MeetingParticipant가 생성된다. Space OWNER/ADMIN은 ACL 관리 override로 승인/거절할 수 있으며, 승인 전에는 회의 접근권이나 SpaceMember가 생기지 않는다.
- D-025: 로컬 개발 DB는 다른 프로젝트의 PostgreSQL과 분리된 PostgreSQL 16 + pgvector 컨테이너를 사용한다. 기본 host port는 `5434`이며 Backend `db` profile과 Flyway가 schema를 적용한다.
- D-026: 원격에 공유된 Flyway migration은 수정하지 않는다. `V1`~`V9` 이후 누락 schema와 제약 보강은 `V10`부터 forward-only migration으로 추가한다.
- D-027: 회의별 전사 생명주기는 `MeetingTranscript` 1개로 관리한다. `status`, `provider`, `language`, `retentionUntil`, `legalHold`, `purgedAt`을 보존하고 `TranscriptSegment`는 기존 `meetingId` 기준 저장을 유지한다.
- D-028: `SourceReference`는 API 응답용 논리 모델로 유지하고 별도 다형 FK 테이블을 만들지 않는다. 보고서/태스크 근거는 `sourceIds` JSON 배열, transcript chunk 근거는 `CHUNK_SOURCE_SEGMENT` 관계로 보존한다.
- D-029: embedding 재생성은 `EmbeddingJob`과 generation으로 추적한다. 새 generation이 완료되기 전까지 기존 active chunk를 유지하고, 완료 시 새 generation을 active로 전환한다.
- D-030: `Meeting.retentionPolicy` DB 값은 `DAYS_7`, `DAYS_30`, `PERMANENT`를 사용하고 기본값은 `DAYS_30`으로 한다. `retentionUntil`은 기간 보존일 때 계산하며 영구 보존이면 null이다.
- D-031: Backend PostgreSQL 전환은 Auth/User, Space/Meeting ACL, Transcript/Report/Task, ProjectKnowledge 원문·상태, AuditLog와 권한 선필터된 AI context 조립까지 담당한다. embedding provider/model, vector 차원/index, `EmbeddingJob`/`EmbeddingChunk` runtime, pgvector similarity query와 AI semantic retriever는 별도 AI/RAG 담당자가 구현한다. Backend 작업은 기존 embedding/vector migration과 `ai/app/rag.py`를 수정하지 않는다.
- D-032: 회의 삭제는 물리 cascade 대신 soft delete를 사용한다. `SCHEDULED` 회의는 `CANCELED`와 `deletedAt`/`deletedBy`를 함께 기록하고, `IN_PROGRESS` 회의는 `409 MEETING_ALREADY_PROCESSING`으로 거부하며, `ENDED` 회의는 상태를 유지한 채 soft delete한다. 삭제 권한은 프로젝트 `OWNER` 또는 해당 회의 active `HOST`만 가지며 `ADMIN`은 기본 거부한다. soft-deleted 회의는 일반 목록/상세/캘린더와 Meeting/Project AI context에서 즉시 제외하고 hard purge, 복구, 유예 기간은 후속 보존 정책으로 둔다.
- D-033: Meeting 수정 상태 전이는 `SCHEDULED -> IN_PROGRESS`, `SCHEDULED -> CANCELED`, `IN_PROGRESS -> ENDED`만 허용한다. 동일 상태 요청은 idempotent하게 처리하고 역전이는 거부한다. 제목과 예정 일시 수정은 `SCHEDULED`에서만 허용하며 수정 권한은 프로젝트 `OWNER`/`ADMIN` 또는 해당 회의 active `HOST`가 가진다.
- D-034: MVP RAG는 `text-embedding-3-small` 1536차원, cosine exact search와 `pg_trgm` 문자열 검색을 RRF로 결합한다. Meeting AI는 상위 근거 5개, Project AI는 상위 근거 8개를 기본값으로 사용한다. HNSW는 권한 선필터 후 후보 5,000개 초과 또는 검색 p95 1초 지속 초과가 측정될 때 도입하고 IVFFlat은 기본 선택에서 제외한다.
- D-035: 검색 권한은 Backend가 요청마다 결정한다. Backend는 Meeting AI의 단일 `meetingId`, Project AI의 `spaceId`와 `allowedMeetingIds`를 만들고, AI는 역할이나 멤버십을 재판단하지 않고 전달받은 범위를 RAG 쿼리에 강제한다. `EmbeddingChunk.scope`는 AI 종류가 아니라 source 소유 범위이며, meeting 산출물은 `meeting`, ProjectKnowledge는 `project`로 저장한다.
- D-036: Target AI 응답은 검색 관련도 gate와 구조화된 `supported`, `answer`, `sourceIds` 결과를 사용한다. 근거 0건 또는 관련도 미달이면 LLM을 호출하지 않고, 존재하지 않는 source ID, 빈 citation, 검증되지 않은 저장성 산출물은 폐기한다. public 응답은 `unsupportedReason`을 `NO_EVIDENCE`, `LOW_RELEVANCE`, `MODEL_UNSUPPORTED`, `UNVERIFIED_OUTPUT` 중 하나로 반환할 수 있다.
- D-037: 문서와 회의 기록의 "재학습"은 모델 fine-tuning이 아니라 검색 인덱스 갱신이다. ProjectKnowledge 생성/수정/복원, transcript 완료, 발화자명 변경, current confirmed report 변경 시 비동기 generation을 만들고, 연속 STT segment마다 작업을 만들지 않는다. 삭제, 보관, 보존 만료는 새 generation을 기다리지 않고 관련 chunk를 즉시 검색에서 제외한다.
- D-038: AI 운영 기준은 원문을 남기지 않는 구조화 로그와 요청 수, p95 지연, unsupported 사유, citation 검증 실패, embedding job 적체/실패 지표를 우선한다. 초기 알림은 provider 오류율 5%, 검색 p95 1초 지속 초과, `UNVERIFIED_OUTPUT` 1%, 최종 job 실패 1건, 가장 오래된 pending job 5분 초과를 기준으로 시작하고 실제 트래픽 기준선에 따라 조정한다.
- D-039: 회의 채팅 첨부파일 RAG의 MVP는 텍스트 검색으로 제한한다. TXT, Markdown, 텍스트 추출 가능한 PDF는 정규화된 추출 텍스트를 기존 `text-embedding-3-small` 1536차원 공간에 저장한다. 이미지 파일과 이미지 전용 PDF는 검색 대상에서 제외하고 visual embedding, OCR/Vision 설명 생성, 원본 기반 멀티모달 답변은 별도 확장 milestone에서 결정한다.
- D-040: Auth는 기존 JDBC `AuthStore`와 `users` 테이블 관리 경계를 유지한다. Workspace와 Backend가 저장하는 AI artifact의 도메인 모델 자체를 JPA entity로 전환하며, 별도 `*Entity`-record 변환 계층은 두지 않는다. 전환 중에도 Flyway만 schema owner이며 `ddl-auto=validate`를 사용한다. `user_id` 계열 FK는 Auth entity 연관관계가 아닌 scalar ID로 유지하고, pgvector 검색/worker의 `embedding_jobs`/`embedding_chunks`는 native SQL/JDBC 경계를 유지한다.
- D-041: 목표 브라우저 인증과 MSA 전환에서는 D-005의 Frontend token 저장을 폐기한다. 별도 Web BFF의 Redis 서버 세션, 암호화 Token Vault와 내부 Auth Service 계약은 `../002-bff-auth-msa/**`를 우선한다. D-005는 현재 구현 설명과 제한된 rollback에만 유효하다.
