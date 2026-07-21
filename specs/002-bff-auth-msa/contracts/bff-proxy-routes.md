# BFF Proxy Route Contract

## Status and Boundary

- Status: Phase 1 current Backend compatibility.
- Browser는 동일 origin의 `/api/v1/*`만 호출하고 목적 URL이나 service 이름을 전달하지 않는다.
- `/api/v1/auth/*`는 BFF Auth controller 전용이며 업무 proxy에 포함하지 않는다.
- legacy `/api/workspace`, `/api/livekit`, `/api/stt`는 proxy하지 않는다.
- path variable은 현재 Backend가 발급하는 entity prefix와 UUID 조합만 허용하고 method/path 조합이 목록과 다르면 `404 ROUTE_NOT_ALLOWED`를 반환한다.
- BFF는 Browser의 `Authorization`, `Cookie`, `Host`, forwarding header를 downstream에 전달하지 않고 Token Manager가 만든 Bearer header만 사용한다.

## Target Cutover Boundary

- T035 target mode에서 BFF는 Auth Service의 `meetingmind-core`, `meetingmind-ai`, `meetingmind-livekit` access token을 audience별로 암호화 TokenBundle에 보관한다. legacy 단일 access token을 target audience에 재사용하지 않는다.
- legacy mode의 compatibility bundle은 이전 Backend가 발급한 단일 token을 기존 Core/AI/LiveKit proxy 경로에만 호환 매핑한다. 이 매핑은 target token으로 전환되지 않으며 target mode 활성화 시 허용되지 않는다.
- target mode는 `BFF_AUTH_MODE=target`, `BFF_AUTH_BASE_URL`, `BFF_AUTH_ISSUER`로 명시적으로 활성화한다. 기본은 `legacy`이며 rollback 중 기존 Backend compatibility client를 유지한다.
- local/test/integration profile에서만 `BFF_AUTH_TEST_WORKLOAD_PRINCIPAL`로 Auth의 test workload header를 보낼 수 있다. production profile에서 값이 설정되면 BFF는 시작을 거부한다.
- target Auth login 또는 refresh 성공 뒤와 Auth profile 수정 성공 뒤, BFF는 Core에 `POST /internal/v1/core/auth-users/projection`을 호출한다. Authorization에는 `meetingmind-core` token만 사용하며 request body의 email/displayName/pictureUrl은 Auth response에서만 파생한다.
- Core는 target token의 signature/issuer/audience를 먼저 검증하고 `sub` UUID로 `auth_user_mappings`를 immutable하게 생성 또는 검증한다. 매핑이 이미 있으면 email identity를 재검증한 뒤 displayName/pictureUrl만 Auth 원본으로 갱신한다. BFF 또는 browser가 Core ID, mapping source, mapping version을 지정할 수 없다.
- 탈퇴에서는 BFF가 동일 Core token으로 `POST /internal/v1/core/account-withdrawal/reservation`을 먼저 호출한다. Auth disable 성공 뒤에만 `complete`, Auth disable 실패 때만 best-effort `cancel`을 호출한다. 세 route는 browser proxy allowlist가 아니며 subject/body user ID를 받지 않는다.
- 이 internal route와 Auth API는 public ingress/browser proxy allowlist에 넣지 않는다. mTLS/SPIFFE workload allowlist와 NetworkPolicy가 BFF→Core 호출을 제한한다.

## Allowed Routes

`{spaceId}`, `{meetingId}`, `{memberId}`, `{invitationId}`, `{participantId}`, `{requestId}`, `{candidateId}`, `{taskId}`, `{reportId}`, `{termId}`는 각각 `space-`, `meeting-`, `space-member-`, `space-invitation-`, `meeting-participant-`, `join-request-`, `task-candidate-`, `task-`, `report-`, `term-` prefix 뒤 UUID를 사용한다. transcription `{sessionId}`만 현재 Backend 계약에 따라 bare UUID다. 아래 목록 외 동적 path 또는 method는 허용하지 않는다.

| Logical Service | Methods and Paths |
| --- | --- |
| Core | `GET /api/v1/dashboard`, `GET|POST /api/v1/spaces`, `PATCH|DELETE /api/v1/spaces/{spaceId}`, `GET|POST /api/v1/spaces/{spaceId}/meetings`, `GET /api/v1/spaces/{spaceId}/members`, `POST /api/v1/spaces/{spaceId}/invitations`, `POST /api/v1/spaces/{spaceId}/invitations/{invitationId}/accept|decline`, `PATCH|DELETE /api/v1/spaces/{spaceId}/members/{memberId}`, `POST /api/v1/spaces/{spaceId}/owner-transfer`, `GET /api/v1/spaces/{spaceId}/project-ai/context-candidates` |
| Core | `GET|POST /api/v1/spaces/{spaceId}/tasks`, `PATCH|DELETE /api/v1/spaces/{spaceId}/tasks/{taskId}` |
| Core | `GET|POST /api/v1/spaces/{spaceId}/terms`, `PATCH|DELETE /api/v1/spaces/{spaceId}/terms/{termId}`, `GET /api/v1/calendar/events` |
| Core | `GET|PATCH|DELETE /api/v1/meetings/{meetingId}`, `GET|POST /api/v1/meetings/{meetingId}/participants`, `PATCH /api/v1/meetings/{meetingId}/participants/{participantId}`, `GET /api/v1/meetings/{meetingId}/join-requests`, `POST /api/v1/meetings/join-requests`, `POST /api/v1/meetings/{meetingId}/join-requests/{requestId}/approve|reject`, `GET /api/v1/meetings/{meetingId}/dialogue` |
| Core | `GET/POST /api/v1/spaces/{spaceId}/knowledge`, `GET/PATCH/DELETE /api/v1/spaces/{spaceId}/knowledge/{knowledgeId}`, `GET /api/v1/meetings/{meetingId}/task-candidates`, `POST /api/v1/meetings/{meetingId}/task-candidates/{candidateId}/confirm`, `POST /api/v1/meetings/{meetingId}/task-candidates/{candidateId}/dismiss`, `GET /api/v1/meetings/{meetingId}/reports`, `GET /api/v1/meetings/{meetingId}/reports/{reportId}`, `POST /api/v1/meetings/{meetingId}/reports/{reportId}/confirm`, `POST /api/v1/meetings/{meetingId}/reports/{reportId}/restore`, `PATCH /api/v1/meetings/{meetingId}/reports/{reportId}`, `GET /api/v1/meetings/{meetingId}/reports/{reportId}/download` |
| Core (AI gateway) | `POST /api/v1/spaces/{spaceId}/ai/chat`, `POST /api/v1/meetings/{meetingId}/ai/chat`, `POST /api/v1/meetings/{meetingId}/terms/explain`, `POST /api/v1/meetings/{meetingId}/reports/generate`, `POST /api/v1/meetings/{meetingId}/task-candidates/generate` |
| LiveKit | `POST /api/v1/meetings/{meetingId}/livekit-token`, `POST /api/v1/meetings/{meetingId}/transcription/start`, `POST /api/v1/meetings/{meetingId}/transcription/{sessionId}/stop` |

Query parameter는 허용된 route의 현재 Backend 계약에만 전달하며 목적지 선택에 사용하지 않는다.

## Header and Response Rules

- Request allowlist: `Content-Type`, `Accept`, BFF가 생성한 `Authorization`.
- Response allowlist: `Content-Type`, `Cache-Control`, `ETag`, `Content-Disposition`. 보고서 Markdown download처럼 Core가 attachment를 반환하는 route는 downstream filename/header를 보존한다.
- downstream `2xx~4xx`는 status와 body를 전달하되 `401`은 T014 Token Manager가 refresh 후 최대 한 번 재시도한다.
- downstream `5xx`, 연결 실패, timeout, circuit open, bulkhead full은 provider raw body 없이 공통 error shape로 정규화한다.
- 상태 변경 요청을 자동 재시도하지 않는다. 인증 `401` 재시도만 T014의 정확히 한 번 경계를 사용한다.

## Phase 1 Resilience Defaults

| Service | Connect / Read Timeout | Bulkhead | Circuit |
| --- | --- | --- | --- |
| Core | 1s / 3s | 동시 64, queue 없음 | 연속 실패 5회, 30s open |
| AI | 1s / 30s | 동시 8, queue 없음 | 연속 실패 3회, 30s open |
| LiveKit | 1s / 2s | 동시 16, queue 없음 | 연속 실패 3회, 15s open |

기본값은 환경 설정으로 조정할 수 있으며 운영 SLO 확정 전 Phase 1 기준이다. timeout은 Backend proxy hop 기준이고 provider 내부 timeout은 각 서비스가 별도로 더 짧게 관리한다.

## Errors

| Status | Code | Meaning |
| --- | --- | --- |
| 404 | `ROUTE_NOT_ALLOWED` | method/path 조합이 allowlist에 없음 |
| 503 | `CORE_SERVICE_UNAVAILABLE` | Core timeout, 5xx, circuit 또는 bulkhead 거부 |
| 503 | `AI_PROVIDER_UNAVAILABLE` | AI route timeout, 5xx, circuit 또는 bulkhead 거부 |
| 503 | `LIVEKIT_SERVICE_UNAVAILABLE` | LiveKit route timeout, 5xx, circuit 또는 bulkhead 거부 |
| 401 | `SESSION_INVALID` | refresh 실패 또는 refresh 뒤 최종 downstream 401 |

AI/LiveKit 실패를 Core 성공이나 mock 응답으로 대체하지 않는다.
