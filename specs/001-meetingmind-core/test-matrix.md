# Test Matrix: Requirement Verification

이 문서는 MeetingMind 요구사항 기준으로 권한, LiveKit/STT, AI scope, RAG, 외부 API 장애 대응,
관측성까지 포함한 검증 기준을 한 곳에 모은 실행 매트릭스다. 초기 작성 범위는 T039, T040,
T094, T168이었고, 현재는 M043 기준으로 AI 운영 검증 항목까지 확장했다.

## Current Status

| Area | Status | Evidence | Remaining Gap |
| --- | --- | --- | --- |
| Space/Meeting authz, LiveKit access | Automated pass | T039, T040, T094, T168 unit/controller tests | guest/product E2E 재확인 |
| AI scope and grounding | Partial | AI unit tests, grounding tests, `ai-harness-strategy.md` | AH-001~AH-014 전항목 자동화 미완료 |
| STT/LiveKit smoke | Partial | 기존 SR-005, `operational-smoke-runbook.md`, 2026-07-25 local deterministic re-run | provider opt-in smoke 실행 기록 부족 |
| AI Report -> Knowledge -> Project AI | Partial | runbook 절차, Backend report/project AI deterministic test | SMK-003, SMK-004 실행 결과 미기록 |
| Guest/ACL negative | Partial | authz matrix, runbook, 기존 ACL automation | SMK-005 브라우저 smoke 미기록 |
| External API resilience | Partial | `contracts/external-reliability.md`, BFF `DownstreamGuardTest`, Backend `AiGatewayGuardTest` | dependency별 provider smoke evidence와 failure matrix 미완료 |
| Prometheus/Grafana observability | Partial | BFF `BffHealthEndpointTest`, Backend `BackendActuatorEndpointTest`, AI `/metrics` unittest | STT/LiveKit custom metric, Grafana provisioning 미완료 |

## Scope

| Task | Target | Test level |
| --- | --- | --- |
| T039 | Space access validation service 또는 policy 계층 | Pure unit test 우선 |
| T040 | Meeting access validation service 또는 policy 계층 | Pure unit test 우선 |
| T094 | LiveKit token 발급 권한 연동 | Service unit test, controller slice test 후보 |
| T168 | Meeting join request 생성/검토 | Domain service unit test, controller unit test |
| M043 | AI scope, RAG, STT/LiveKit smoke, external API resilience, observability | Unit test + opt-in smoke + manual E2E split |

## Source Criteria

| Source | Criteria used |
| --- | --- |
| `requirements/permissions.md` | SpaceRole과 MeetingParticipant 분리, HOST/삭제/AI 접근 차단 규칙 |
| `requirements/status-values.md` | `Meeting.status`, `MeetingParticipant.accessStatus` canonical values |
| `requirements/policies.md` | default-deny, 회의 삭제 기본 OWNER/HOST 전용 |
| `requirements/functional-requirements-detail.md` | FR-MREG-07, FR-ACL-01~07, FR-CALL-01 성공/실패 기준 |
| `requirements/non-functional-requirements.md` | AI scope, observability, reliability 기준 |
| `requirements/performance.md` | timeout, token budget, provider failure 기준 |
| `specs/001-meetingmind-core/clarify.md` | D-015~D-020 결정사항 |
| `specs/001-meetingmind-core/contracts/common.md` | 공통 error code |
| `specs/001-meetingmind-core/contracts/meeting-api.md` | Meeting delete, participant role/accessStatus 변경 계약 |
| `specs/001-meetingmind-core/contracts/live-stt-api.md` | Target LiveKit token endpoint 권한 계약 |
| `specs/001-meetingmind-core/contracts/external-reliability.md` | 외부 API timeout/retry/fallback 기준 |
| `specs/001-meetingmind-core/contracts/observability.md` | Prometheus/Grafana metric 기준 |
| `specs/001-meetingmind-core/ai-harness-strategy.md` | AH/SMK/EX/OBS 검증 정의 |
| `specs/001-meetingmind-core/operational-smoke-runbook.md` | provider opt-in smoke 절차 |

## Result Conventions

| Result | Meaning |
| --- | --- |
| `ALLOW` | 권한 검증이 통과하고 후속 조회/수정/토큰 발급으로 진행한다. |
| `DENY_401` | 인증 사용자가 없거나 access token 검증에 실패해 `UNAUTHORIZED`로 거부한다. |
| `DENY_403_SPACE` | Space 권한이 없어 `SPACE_ACCESS_DENIED`로 거부한다. |
| `DENY_403_MEETING` | 회의 접근 또는 회의 작업 권한이 없어 `MEETING_ACCESS_DENIED`로 거부한다. |
| `NOT_FOUND_404` | 존재하지 않거나 접근 범위 밖으로 숨겨야 하는 resource를 not found로 처리한다. |
| `REJECT_400` | role/status 등 입력값 자체가 유효하지 않아 `INVALID_REQUEST`로 거부한다. |
| `REJECT_409` | 마지막 HOST 제거 등 현재 상태와 충돌해 거부한다. |
| `SERVICE_503` | LiveKit 설정 누락 등 외부 service 설정 문제로 거부한다. |
| `ISSUE_TOKEN` | LiveKit token을 발급한다. |

구현 시 예외 타입은 code를 명시적으로 검증한다. 단순히 exception 발생만 확인하면 실패한 테스트로 본다.

## T039 Space Access Validation

### Unit Cases

| ID | Scenario | Given | Action | Expected | Source |
| --- | --- | --- | --- | --- | --- |
| S-001 | OWNER Space 접근 허용 | active `SpaceMember(role=OWNER)` | Space read/access check | `ALLOW` | Permission matrix |
| S-002 | ADMIN Space 접근 허용 | active `SpaceMember(role=ADMIN)` | Space read/access check | `ALLOW` | Permission matrix |
| S-003 | MEMBER Space 기본 접근 허용 | active `SpaceMember(role=MEMBER)` | Space read/access check | `ALLOW` | Permission matrix |
| S-004 | SpaceMember 없음 | user is not member of target Space | Space read/access check | `DENY_403_SPACE` | POL-AUTHZ-01 |
| S-005 | 제거된 SpaceMember | member row removed, inactive, or soft-deleted if such state exists | Space read/access check | `DENY_403_SPACE` | FR-ACL-05 |
| S-006 | Space 없음 | unknown `spaceId` | Space read/access check | `NOT_FOUND_404` | Common errors |
| S-007 | OWNER member 관리 허용 | active `OWNER` | invite, role change, member removal check | `ALLOW` | Permission matrix |
| S-008 | ADMIN member 관리 허용 | active `ADMIN` | invite, role change, member removal check | `ALLOW` | Permission matrix |
| S-009 | MEMBER member 관리 거부 | active `MEMBER` | invite, role change, member removal check | `DENY_403_SPACE` | Permission matrix |
| S-010 | Guest는 Space 권한 없음 | user only has `MeetingParticipant(participantType=guest)` | Project Knowledge or Space member API access | `DENY_403_SPACE` | Permission matrix |

### Pass Criteria

- Space access service는 default-deny로 동작한다.
- SpaceRole과 MeetingParticipant를 섞어 판정하지 않는다.
- guest participant는 특정 meeting 권한만 만들며 Space 전체 권한을 만들지 않는다.
- 권한 실패는 code까지 검증한다.

## T040 Meeting Access Validation

### Read and Context Access

| ID | Scenario | Given | Action | Expected | Source |
| --- | --- | --- | --- | --- | --- |
| M-001 | ACTIVE HOST 접근 허용 | `MeetingParticipant(role=HOST, accessStatus=ACTIVE)` | meeting detail, transcript, Meeting AI context check | `ALLOW` | FR-MREG-07 |
| M-002 | ACTIVE EDITOR 접근 허용 | `MeetingParticipant(role=EDITOR, accessStatus=ACTIVE)` | meeting detail, transcript, Meeting AI context check | `ALLOW` | FR-MREG-07 |
| M-003 | ACTIVE VIEWER 접근 허용 | `MeetingParticipant(role=VIEWER, accessStatus=ACTIVE)` | meeting detail, transcript, Meeting AI context check | `ALLOW` | FR-MREG-07 |
| M-004 | REVOKED participant 차단 | `MeetingParticipant(accessStatus=REVOKED)` | meeting detail, transcript, LiveKit, Meeting AI context check | `DENY_403_MEETING` | D-017 |
| M-005 | participant 없음 차단 | Space `MEMBER`, no MeetingParticipant | meeting detail or transcript check | `DENY_403_MEETING` | FR-ACL-03 |
| M-006 | Space OWNER override 허용 | active Space `OWNER`, no MeetingParticipant | meeting detail or transcript check | `ALLOW` | FR-ACL-05 |
| M-007 | Space ADMIN override 허용 | active Space `ADMIN`, no MeetingParticipant | meeting detail or transcript check | `ALLOW` | FR-ACL-05 |
| M-008 | 비멤버 차단 | no SpaceMember and no MeetingParticipant | meeting detail or transcript check | `DENY_403_MEETING` | FR-MREG-07 |
| M-009 | guest는 지정 회의만 허용 | `participantType=guest`, same meeting, `ACTIVE` | same meeting access check | `ALLOW` | Permission matrix |
| M-010 | guest의 다른 회의 접근 차단 | `participantType=guest` for another meeting | other meeting access check | `DENY_403_MEETING` | Permission matrix |
| M-011 | 회의 없음 | unknown `meetingId` | meeting access check | `NOT_FOUND_404` | Common errors |

### Role Hierarchy

| ID | Scenario | Given | Action | Expected | Source |
| --- | --- | --- | --- | --- | --- |
| M-020 | VIEWER 조회 허용 | `role=VIEWER`, `ACTIVE` | view/read permission check | `ALLOW` | FR-ACL-04 |
| M-021 | VIEWER 수정 거부 | `role=VIEWER`, `ACTIVE` | report edit or speaker edit permission check | `DENY_403_MEETING` | FR-ACL-04 |
| M-022 | EDITOR 조회/수정 허용 | `role=EDITOR`, `ACTIVE` | view and edit permission check | `ALLOW` | FR-ACL-04 |
| M-023 | EDITOR 삭제 거부 | `role=EDITOR`, `ACTIVE` | meeting delete permission check | `DENY_403_MEETING` | FR-ACL-07 |
| M-024 | HOST 조회/수정/삭제 허용 | `role=HOST`, `ACTIVE` | view, edit, delete permission check | `ALLOW` | FR-ACL-04, FR-ACL-07 |
| M-025 | Space ADMIN 삭제 기본 거부 | active Space `ADMIN`, no explicit delete exception policy | meeting delete permission check | `DENY_403_MEETING` | D-020 |
| M-026 | Space OWNER 삭제 허용 | active Space `OWNER` | meeting delete permission check | `ALLOW` | FR-ACL-07 |

### Participant Mutation

| ID | Scenario | Given | Action | Expected | Source |
| --- | --- | --- | --- | --- | --- |
| M-040 | OWNER participant 부여 허용 | actor Space `OWNER`, target active Space member | add participant | `ALLOW` | FR-ACL-01 |
| M-041 | ADMIN participant 부여 허용 | actor Space `ADMIN`, target active Space member | add participant | `ALLOW` | FR-ACL-01 |
| M-042 | HOST participant 부여 허용 | actor `HOST`, target active Space member | add participant | `ALLOW` | FR-ACL-01 |
| M-043 | MEMBER participant 부여 거부 | actor Space `MEMBER`, not HOST | add participant | `DENY_403_MEETING` | FR-ACL-01 |
| M-044 | 비멤버에게 member participant 부여 거부 | target has no SpaceMember | add `participantType=member` | `DENY_403_MEETING` | FR-ACL-01 |
| M-045 | role 값 오류 거부 | role outside `HOST`, `EDITOR`, `VIEWER` | update participant role | `REJECT_400` | Common role values |
| M-046 | accessStatus 값 오류 거부 | status outside `ACTIVE`, `REVOKED` | update participant accessStatus | `REJECT_400` | Status values |
| M-047 | 마지막 active HOST 강등 거부 | exactly one `ACTIVE HOST` remains | change HOST to EDITOR/VIEWER | `REJECT_409` | D-018 |
| M-048 | 마지막 active HOST 회수 거부 | exactly one `ACTIVE HOST` remains | change accessStatus to `REVOKED` | `REJECT_409` | D-018 |
| M-049 | 마지막 active HOST 제거 거부 | exactly one `ACTIVE HOST` remains | remove participant | `REJECT_409` | D-018 |
| M-050 | 다른 HOST가 있으면 기존 HOST 변경 허용 | at least two `ACTIVE HOST` participants | downgrade or revoke one HOST | `ALLOW` | D-018 |
| M-051 | HOST 회의방 퇴장 권한 유지 | HOST leaves WebRTC room without ending meeting | leave room event | participant role/status unchanged | D-018 |
| M-052 | HOST 회의 종료 허용 | actor `HOST`, meeting `IN_PROGRESS` | end meeting | status becomes `ENDED` | D-018 |

### SpaceMember Removal Impact

| ID | Scenario | Given | Action | Expected | Source |
| --- | --- | --- | --- | --- | --- |
| M-060 | SpaceMember 제거 시 member participant 회의 단독 전환 | removed user has same Space `participantType=member` participants | remove SpaceMember | those participants become `participantType=guest` and remain `ACTIVE` | D-016 |
| M-061 | SpaceMember 제거 후 프로젝트 접근 차단과 회의 접근 유지 | removed user still has active MeetingParticipant | project detail, Project AI, meeting detail, LiveKit, Meeting AI check | project/Project AI denied, meeting-scoped access allowed | D-016, D-017 |
| M-062 | guest participant는 SpaceMember 제거 영향 없음 | guest participant has no SpaceMember row | remove unrelated SpaceMember | guest participant unchanged | D-016 |

### Pass Criteria

- `ACTIVE`만 회의 접근 권한으로 인정한다.
- `REVOKED`는 조회, 수정, LiveKit token, Meeting AI, Project AI meeting context 접근을 모두 차단한다.
- owner/admin override는 읽기와 관리 권한에서만 문서화된 범위로 적용한다.
- ADMIN delete는 explicit exception policy가 없으면 실패해야 한다.
- 마지막 active HOST 보호는 role 변경, accessStatus 변경, participant 제거에 모두 적용한다.

## T094 LiveKit Token Authorization

### Unit and Slice Cases

| ID | Scenario | Given | Action | Expected | Source |
| --- | --- | --- | --- | --- | --- |
| L-001 | 인증 없음 | no access token or invalid access token | request target LiveKit token | `DENY_401` | Common auth rule |
| L-002 | 회의 없음 | authenticated user, unknown `meetingId` | request token | `NOT_FOUND_404` | live-stt API |
| L-003 | ACTIVE participant token 발급 | authenticated user has `MeetingParticipant(accessStatus=ACTIVE)` | request token | `ISSUE_TOKEN` | FR-CALL-01 |
| L-004 | REVOKED participant 차단 | authenticated user has `MeetingParticipant(accessStatus=REVOKED)` | request token | `DENY_403_MEETING` | D-017 |
| L-005 | participant 없음 차단 | authenticated Space `MEMBER`, no MeetingParticipant | request token | `DENY_403_MEETING` | FR-ACL-03 |
| L-006 | Space OWNER override token 발급 | authenticated active Space `OWNER`, no MeetingParticipant | request token | `ISSUE_TOKEN` | FR-ACL-05 |
| L-007 | Space ADMIN override token 발급 | authenticated active Space `ADMIN`, no MeetingParticipant | request token | `ISSUE_TOKEN` | FR-ACL-05 |
| L-008 | guest는 지정 회의 token만 발급 | authenticated guest participant for same meeting | request token | `ISSUE_TOKEN` | Permission matrix |
| L-009 | guest의 다른 회의 token 차단 | guest participant belongs to another meeting | request other meeting token | `DENY_403_MEETING` | Permission matrix |
| L-010 | 종료된 회의 token 차단 | meeting `ENDED` | request token | `DENY_403_MEETING` | FR-CALL-01 |
| L-011 | 취소된 회의 token 차단 | meeting `CANCELED` | request token | `DENY_403_MEETING` | Status values |
| L-012 | LiveKit 설정 누락 | authorized user, missing LiveKit env/config | request token | `SERVICE_503` | live-stt API |
| L-013 | Token identity는 인증 사용자 기준 | authenticated user id differs from request body identity | request token | issued token identity equals authenticated user id | D-015 |
| L-014 | Token room은 meeting 기준 | authorized user | request token | issued token room equals target meeting room, not arbitrary input | live-stt API |

### Pass Criteria

- Target endpoint는 request body의 `identity`를 신뢰하지 않는다.
- LiveKit token 발급 전 access token 사용자와 meeting 권한을 모두 확인한다.
- 발급 token은 Space 전체 권한을 포함하지 않는다.
- `LIVE_TOKEN_ISSUED` audit event는 성공 케이스에서만 기록한다.

## T168 Meeting Join Request Approval

| ID | Scenario | Given | Action | Expected | Source |
| --- | --- | --- | --- | --- | --- |
| J-001 | raw code 신청 | authenticated nonparticipant, valid joinCode | create join request | `PENDING`, target meeting resolved | FR-MREG-02~03 |
| J-002 | URL 신청 | URL contains valid `joinCode` query | create join request | `PENDING`, same target meeting resolved | FR-MREG-02~03 |
| J-003 | 잘못된 코드 차단 | unknown code | create join request | `DENY_403_MEETING`, meeting existence not distinguished | FR-MREG-03 |
| J-004 | 코드 없는 URL 차단 | URL has no `joinCode` query | create join request | `DENY_403_MEETING` | FR-MREG-03 |
| J-005 | pending 중복 차단 | same user/meeting has `PENDING` request | create another request | `REJECT_400` | Data constraint |
| J-006 | 기존 접근권 사용자 신청 차단 | same user has active MeetingParticipant | create join request | `REJECT_400` | FR-MREG-02 |
| J-007 | 승인 전 접근권 없음 | request is `PENDING` | inspect participant/access | no participant, meeting access denied | FR-MREG-02, FR-MREG-07 |
| J-008 | active HOST 승인 | actor is active HOST without Space override | approve pending request | `APPROVED`, VIEWER participant created | D-022 |
| J-009 | OWNER/ADMIN 승인 override | actor is active Space OWNER/ADMIN | approve pending request | `APPROVED`, VIEWER participant created | Permission matrix |
| J-010 | VIEWER/EDITOR 승인 차단 | actor has no participant-management permission | approve/reject request | `DENY_403_MEETING` | FR-ACL-01 |
| J-011 | guest 승인 결과 | applicant is not SpaceMember | approve request | `participantType=guest`, no SpaceMember created | D-008, D-022 |
| J-012 | member 승인 결과 | applicant is active SpaceMember | approve request | `participantType=member` | D-022 |
| J-013 | 승인 재처리 차단 | request is `APPROVED` | approve or reject again | `REJECT_400` | Status transition |
| J-014 | 거절 재처리 차단 | request is `REJECTED` | approve or reject again | `REJECT_400` | Status transition |

### Pass Criteria

- joinCode는 meeting ID에서 결정적으로 만들지 않고 추측하기 어려운 값으로 생성한다.
- JoinRequest에는 joinCode 원문을 복제 저장하지 않는다.
- 승인 전에는 MeetingParticipant, 회의 접근권, SpaceMember가 생기지 않는다.
- 승인 시 기본 role은 `VIEWER`이며 Space membership 여부는 participant type만 결정한다.
- 검토 권한과 상태 전이는 Backend domain 경계에서 강제한다.

## M033 Meeting CRUD PostgreSQL End-to-End

| ID | Scenario | Given | Action | Expected | Source |
| --- | --- | --- | --- | --- | --- |
| C-001 | Space 회의 목록 ACL | OWNER와 active participant, 권한 없는 member가 같은 Space 회의를 조회 | `GET /spaces/{spaceId}/meetings` | actor가 읽을 수 있는 active meeting만 반환 | FR-MREG-05, FR-MREG-07 |
| C-002 | 목록 filter 검증 | valid/invalid status와 from/to | status/date filter 조회 | valid 범위만 반환, invalid 또는 from>to는 `REJECT_400` | FR-MREG-05 |
| C-003 | 상세 조회 | OWNER/ADMIN 또는 active participant | `GET /meetings/{meetingId}` | meeting과 participant, nullable `myRole` 반환 | FR-MREG-06, FR-MREG-07 |
| C-004 | 제목·일정 수정 | meeting `SCHEDULED`, actor OWNER/ADMIN/HOST | PATCH title/scheduledAt | PostgreSQL 갱신 후 재조회 값 일치 | FR-MREG-04 |
| C-005 | 진행 시작·종료 | `SCHEDULED` 또는 `IN_PROGRESS` | PATCH status | `SCHEDULED -> IN_PROGRESS -> ENDED`, startedAt/endedAt 기록 | Status values, D-033 |
| C-006 | 역방향 상태 전이 차단 | `ENDED`, `CANCELED` 또는 canonical 역방향 | PATCH status | `REJECT_400`, 기존 row 불변 | D-033 |
| C-007 | SCHEDULED 삭제 | actor OWNER/HOST, meeting `SCHEDULED` | DELETE | `CANCELED`과 deleted metadata가 같은 transaction에 기록 | FR-MREG-04, D-032 |
| C-008 | 진행 중 삭제 차단 | meeting `IN_PROGRESS` | DELETE | `REJECT_409`, deleted metadata 없음 | D-032 |
| C-009 | ADMIN 삭제 차단 | actor Space ADMIN, not HOST | DELETE | `DENY_403_MEETING` | FR-ACL-07, D-020 |
| C-010 | 삭제 회의 제외 | soft-deleted meeting | list/detail/calendar/Meeting AI/Project AI 조회 | 목록·후보 제외, direct detail/Meeting AI `NOT_FOUND_404` | D-032 |
| C-011 | 재시작 영속성 | meeting create/update 후 Backend restart | login 후 list/detail | 수정 값과 ACL이 PostgreSQL에서 유지 | M032 runtime boundary |
| C-012 | Frontend target 경계 | target Space와 legacy mock Space 존재 | UI create/update/delete | target은 API 재조회만 반영, mock과 혼합 없음 | M033 plan |

### Pass Criteria

- Meeting mutation은 row lock과 audit를 포함한 transaction으로 처리한다.
- `deleted_at`, `deleted_by`는 둘 다 null이거나 둘 다 기록된다.
- soft-deleted meeting은 관계형 조회와 AI context 선필터 단계에서 제외한다.
- hard purge, restore, grace period는 M033 성공으로 간주하지 않고 후속 운영 정책으로 남긴다.

## M035 Meeting Attachment RAG

Deferred from the current delivery phase. The scenarios remain the acceptance baseline for the next phase and are not executed in this phase.

| ID | Scenario | Given | Action | Expected | Source |
| --- | --- | --- | --- | --- | --- |
| AT-001 | private upload completion | active MeetingParticipant and issued upload session | allowed TXT object with matching checksum completes | attachment moves `PENDING_UPLOAD -> PROCESSING`, message may publish, extractor job is scheduled | POL-UPLOAD-01, NFR-SEC-06 |
| AT-002 | unsupported visual file | allowed PNG or image-only PDF | object completes and extractor classifies source | file remains downloadable as `UNSUPPORTED`, no EmbeddingJob/Chunk is created | D-039, D-044 |
| AT-003 | attachment RAG citation | `READY` text PDF attachment with page anchors | Meeting AI and authorized Project AI query | only scoped attachment chunk is eligible and response cites attachment/file/page anchor | FR-MBOT-01~03, NFR-AZ-01~04 |
| AT-004 | ACL and deletion negative | same-Space user lacks active MeetingParticipant or attachment is deleted/expired | upload/list/download/RAG query | API denies user; deleted/expired attachment has no download URL or active chunk before provider call | NFR-AZ-03~05, NFR-DATA-01~04 |
| AT-005 | checksum and MIME validation | upload metadata disagrees with stored object | complete endpoint | checksum/MIME/size error; message cannot reference attachment | NFR-SEC-06, POL-UPLOAD-01 |

## M038 STT-to-RAG PostgreSQL Flow

| ID | Scenario | Given | Action | Expected | Source |
| --- | --- | --- | --- | --- | --- |
| SR-001 | STT callback dialogue 저장 | active HOST와 `PROCESSING` MeetingTranscript | provider callback text 2건 수신 후 session close | MeetingSpeaker/TranscriptSegment 2건, status `COMPLETED` | FR-STT-01, FR-STT-02, FR-STT-05 |
| SR-002 | 완료 전사 job 생성 | `PROCESSING` transcript와 저장된 segment | `COMPLETED` 전환 | `TRANSCRIPT_COMPLETED` embedding job 정확히 1건 | D-037, V12 trigger |
| SR-003 | 전사 입력 RAG 정합성 | 200개 completed transcript segment | deterministic worker 실행 후 Meeting search 100회 | Meeting/Space scope가 일치하는 결과만 반환 | FR-MBOT-01~04, NFR-DATA-01~02 |
| SR-004 | 로컬 검색 성능 | SR-003과 동일 데이터 | 100회 retrieval latency 측정 | deterministic provider 기준 p95 < 1초 | PERF-RAG-01 |
| SR-005 | 외부 provider smoke | 실제 Clova secret, LiveKit egress, public callback URL | target transcription start -> audio -> stop -> dialogue | 실시간 provider callback, egress, target API를 포함한 실제 흐름 성공 | FR-CALL-01, FR-STT-01~05 |
| SR-007 | OpenAI RAG provider 통합 | `RUN_OPENAI_RAG_INTEGRATION=true`, OpenAI key, Flyway V12 이상 PostgreSQL | 한국어 STT를 완료 처리하고 worker 색인 후 Project retrieval 실행 | vector(1536), allowed meeting만 반환, 빈 allowed/cross-space 차단, PostgreSQL hybrid retrieval p95 < 1초 | T275, PERF-RAG-01 |
| SR-008 | Korean grounded provider 평가 | `RUN_OPENAI_GROUNDED_EVAL=true`, OpenAI key | 단일 회의 근거 있음 15건과 무관한 근거의 질문 15건을 internal Meeting AI handler로 평가 | false-supported <= 5%, supported answer/citation >= 95%, provider-inclusive p95를 기록 | T275, NFR-AI-01 |

### Pass Criteria

- target meeting session은 provider callback text를 보존 정책 밖 파일에 복사하지 않는다.
- segment마다 embedding job을 만들지 않고 transcript 완료 transaction에서 하나만 만든다.
- local deterministic p95는 DB/retriever 경계만 측정한다. OpenAI provider 품질·외부 latency와 LiveKit egress는 SR-005 별도 환경에서 측정한다.

### Execution Status

- SR-001~SR-004: local PostgreSQL integration과 deterministic embedding provider로 통과했다. 200개 segment, 100회 Meeting retrieval에서 local p95는 `8.85 ms`였다.
- SR-005: 48 kHz WebSocket -> resampler -> Cloud STT 경로에서 Korean PCM 전사 callback 65건을 확인했다. 별도 opt-in target smoke는 실제 Cloud callback -> JPA dialogue `COMPLETED` -> `TRANSCRIPT_COMPLETED` job 1건을 검증했다. valid LiveKit Cloud credential, 임시 ngrok callback, browser LiveKit client에서 published audio track의 Egress를 시작해 callback transcript 60건을 확인했다. Egress API는 `ws(s)` signalling URL을 `http(s)` API URL로 정규화한다.
- SR-006: 기본 단위 test에서 target Egress WebSocket 종료가 `MeetingTranscript=COMPLETED`로 종결되는지, legacy 세션은 유지되는지, Egress stop 실패가 `FAILED` 종결과 `503 STT_PROVIDER_UNAVAILABLE`로 변환되는지 검증한다.
- SR-007: `text-embedding-3-small` 실제 provider로 통과했다. 한국어 STT fixture의 worker 색인, `vector(1536)`, allowed meeting만 반환, 빈 allowed/cross-space 차단을 확인했고 PostgreSQL hybrid retrieval 100회 p95는 `14.98 ms`였다. provider chat 품질은 SR-008/T275에서 별도로 평가했다.
- SR-008: `gpt-4.1-mini` 실제 provider로 2026-07-20에 30건을 실행했다. 근거 없음 15건의 false-supported는 `0%`, 근거 있음 15건의 supported answer/citation 정확도는 각각 `100%`, provider-inclusive p95는 `1,933.02 ms`였다. 이 평가는 provider를 포함하므로 SR-007의 PostgreSQL retrieval p95와 직접 비교하지 않는다.

## M043 AI Reliability Harness and Operational Verification

### Requirement Coverage

| Group | Requirement Baseline | Verification Target | Current Status |
| --- | --- | --- | --- |
| AI scope and grounding | NFR-AZ-01~04, NFR-AI-01~04, PERF-TOKEN-01~06 | Meeting AI/Project AI scope, citation, unsupported branch, token budget | Partial |
| RAG retrieval | PERF-BE-04, PERF-RAG-01~03 | permission prefilter, retrieval latency, context assembly latency | Partial |
| STT/LiveKit smoke | FR-CALL, FR-STT, PERF-EXT-02~03 | live join, token, STT start/stop, dialogue persistence | Manual/Partial |
| AI Report to Knowledge | FR-RPT, FR-PBOT, NFR-DATA | transcript -> report -> confirm -> knowledge/RAG availability | Partial |
| External provider resilience | NFR-REL-01~02, NFR-AVAIL-02, PERF-EXT-01~05 | timeout, retry, fallback, safe error shape | Partial |
| Monitoring | NFR-LOG-01~02, PERF-OBS-01 | latency, source count, token usage, provider failure, no PII logs | Partial |

### AI Harness Cases

| ID | Scenario | Given | Action | Expected | Source |
| --- | --- | --- | --- | --- | --- |
| AH-001 | Meeting AI single-meeting scope | user can access meeting A and same-space meeting B exists | ask Meeting AI in A about data only in B | B is never searched or cited | NFR-AZ-01, FR-MBOT |
| AH-002 | Meeting AI participant denial | user is not an active participant of meeting A | ask Meeting AI in A | request is denied before RAG/provider call | NFR-AZ-03 |
| AH-003 | Project AI allowed meetings | `allowedMeetingIds=[A]`, meeting B exists | ask Project AI | ProjectKnowledge and A-only meeting source are eligible | NFR-AZ-02 |
| AH-004 | Empty allowed meetings | `allowedMeetingIds=[]` | ask Project AI | no meeting source is eligible; ProjectKnowledge remains eligible | NFR-AZ-02 |
| AH-005 | No evidence gate | retrieval returns no result | ask AI | provider is not called, `unsupported=true`, `NO_EVIDENCE` | NFR-AI-01, PERF-TOKEN-06 |
| AH-006 | Low relevance gate | retrieval score is below threshold | ask AI | provider is not called, `unsupported=true`, `LOW_RELEVANCE` | NFR-AI-01 |
| AH-007 | Citation validation | provider returns unknown source ID | parse provider output | supported answer is rejected or downgraded to unsupported | NFR-AI-02 |
| AH-008 | Prompt injection in source | transcript/knowledge contains role-change instruction | ask AI | source instruction is ignored and answer remains evidence-bound | NFR-AI-03 |
| AH-009 | Token budget shrink | evidence exceeds context limit | assemble context | low-score evidence is removed first without widening scope (`T451`; 상한은 아직 token이 아니라 건수 — `T451.1`) | PERF-TOKEN-01~05 |
| AH-010 | Provider timeout | provider exceeds configured timeout | ask AI | `503 AI_PROVIDER_UNAVAILABLE`, no raw provider message | PERF-EXT-01, NFR-REL-01 |
| AH-011 | Report/task source validation | candidate includes invalid source IDs | generate report/task candidates | invalid candidates are removed; no fake source is emitted | NFR-AI-02 |
| AH-012 | Terms exact match | transcript contains registered term | render/explain term | glossary answer is local, no LLM call | PERF-AI-06, PERF-TOKEN-06 |
| AH-013 | Project AI history isolation | other user or space has chat history | ask follow-up question | only current `spaceId + userId` history is used as untrusted context | NFR-AZ-04 |
| AH-014 | Log redaction | request includes prompt/transcript/answer/API key | inspect logs/metrics | no raw prompt, STT text, answer, secret, token, or PII is logged | NFR-LOG-01, PERF-OBS-01 |

### Smoke Cases

| ID | Flow | Required Environment | Expected |
| --- | --- | --- | --- |
| SMK-001 | deterministic AI harness | local DB, deterministic provider | AH core gates pass without paid provider |
| SMK-002 | LiveKit + STT | LiveKit credential, STT provider key, public callback URL when needed | token, join, STT start/stop, dialogue persistence pass |
| SMK-003 | AI Report -> Knowledge | completed transcript, report provider, embedding worker | confirmed report creates searchable knowledge/RAG source |
| SMK-004 | Project AI confirmed report query | SMK-003 result | Project AI answers from confirmed report with citation |
| SMK-005 | Guest/ACL negative | guest account and meeting participant variants | guest sees only allowed meeting features and cannot access wider Space data |

Execution procedure: see `operational-smoke-runbook.md`. Local deterministic checks must be
run separately from provider opt-in smoke. Provider credentials, audio samples, and public
callback URLs are never required for default CI.

### External API Resilience Cases

| ID | Dependency | Failure | Expected |
| --- | --- | --- | --- |
| EX-001 | Google OAuth | invalid client/origin/token | 401, no retry loop, safe message |
| EX-002 | LiveKit | token endpoint timeout/failure | normalized failure, retry action, token value never logged |
| EX-003 | Soniox/OpenAI STT | reconnect/failure during live session | reconnecting/failed status, no duplicate committed segment |
| EX-004 | OpenAI generation | timeout/malformed output | 503 provider unavailable or unsupported, raw response hidden |
| EX-005 | OpenAI embedding | transient failure | embedding job retry/backoff, user sees indexing delayed |
| EX-006 | AI service | service unavailable | Core/BFF returns normalized 503 with traceId |
| EX-007 | Redis/session | Redis unavailable | auth/session unavailable is explicit; no silent partial auth |
| EX-008 | PostgreSQL/pgvector | query timeout | service unavailable or delayed indexing; permission scope is not bypassed |

### Monitoring Cases

| ID | Metric Target | Expected |
| --- | --- | --- |
| OBS-001 | AI request duration | endpoint/model/supported/unsupported reason visible |
| OBS-002 | token usage | model and bucketed usage visible; prompt/answer not logged |
| OBS-003 | RAG retrieval | scope/source type/duration/evidence count visible |
| OBS-004 | STT provider | provider latency and failure count visible |
| OBS-005 | LiveKit token | token latency/failure visible without token value |
| OBS-006 | downstream guard | BFF/Core/AI/LiveKit circuit open and bulkhead rejection visible |
| OBS-007 | Grafana dashboard | STT, AI, RAG, external provider, circuit health panels exist |

### Execution Status

- AI harness strategy is defined in `ai-harness-strategy.md`.
- External timeout/retry/fallback/user-message baseline is fixed in `contracts/external-reliability.md`.
- Backend/Core AI gateway clients now share semaphore bulkhead and failure-threshold circuit guard coverage with dedicated unit tests.
- AH-005, AH-007, AH-010, and parts of AH-014 have partial evidence from existing AI grounding/provider tests.
- AH-004, AH-009, and AH-014 have additional local unit coverage in `ai/tests/test_meeting_ai.py`: empty `allowedMeetingIds` remains meeting source 0, provided meeting sources are rejected when no meeting is allowed, source JSON/report provider context keeps only the first allowed sources, and supported response logs do not include question/source/answer text.
- AH-008, report generation untrusted context, and task extraction context limit are now covered by `ai/tests/test_meeting_ai.py`. The suite also validates the current 3-value provider contract `(text, model, usage)` so harness regressions fail at unittest time instead of import/runtime time.
- SMK-001~SMK-005 now have an execution split in `operational-smoke-runbook.md`: local deterministic checks, PostgreSQL-backed integration checks, STT provider opt-in, AI on-prem/OpenAI-compatible opt-in, and product E2E manual steps.
- 2026-07-25 기준 `SMK-001` local deterministic baseline은 다시 실행해 PASS로 기록했다. provider/env/browser가 필요한 `SMK-002~SMK-005`는 여전히 opt-in/manual 영역이다.
- SR-005, SR-007, and SR-008 remain the current strongest STT/RAG/provider evidence.
- T438로 Backend/Core -> AI gateway bulkhead/circuit 기준선과 guard 테스트가 추가됐다. 남은 범위는 STT gateway와 AI 내부 provider worker 경계의 공통 guard 정리다.
- T439로 BFF `/actuator/prometheus`, Backend `/actuator/prometheus`, AI `/metrics` 기준선과 metric name contract는 추가됐다. 다만 STT/LiveKit custom metric과 Grafana provisioning json은 아직 남아 있다.
- SMK-003~SMK-005, token-budget regression, full external API provider-failure execution matrix, and Grafana dashboard provisioning are not yet complete.

## Minimum Implementation Order

1. T039 Space access policy를 pure unit test로 먼저 고정한다.
2. T040 Meeting access policy를 pure unit test로 고정한다.
3. T094 LiveKit token service test에서 T040 policy를 mock 또는 fake로 주입한다.
4. Controller/API test는 service unit test가 통과한 뒤 401/403/404/503 응답 shape만 얇게 확인한다.

## Required Verification

| Area | Command |
| --- | --- |
| Backend unit tests | `cd backend && ./gradlew test` |
| Markdown/diff sanity | `git diff --check` |

구현 PR에서 task를 `[x]`로 바꾸려면 위 command 결과 또는 미실행 사유를 `specs/001-meetingmind-core/implement.md`에 기록한다.
