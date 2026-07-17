# BFF Proxy Route Contract

## Status and Boundary

- Status: Phase 1 current Backend compatibility.
- Browser는 동일 origin의 `/api/v1/*`만 호출하고 목적 URL이나 service 이름을 전달하지 않는다.
- `/api/v1/auth/*`는 BFF Auth controller 전용이며 업무 proxy에 포함하지 않는다.
- legacy `/api/workspace`, `/api/livekit`, `/api/stt`는 proxy하지 않는다.
- path variable은 현재 Backend가 발급하는 entity prefix와 UUID 조합만 허용하고 method/path 조합이 목록과 다르면 `404 ROUTE_NOT_ALLOWED`를 반환한다.
- BFF는 Browser의 `Authorization`, `Cookie`, `Host`, forwarding header를 downstream에 전달하지 않고 Token Manager가 만든 Bearer header만 사용한다.

## Allowed Routes

`{spaceId}`, `{meetingId}`, `{memberId}`, `{participantId}`, `{requestId}`, `{candidateId}`, `{reportId}`는 각각 `space-`, `meeting-`, `space-member-`, `meeting-participant-`, `join-request-`, `task-candidate-`, `report-` prefix 뒤 UUID를 사용한다. transcription `{sessionId}`만 현재 Backend 계약에 따라 bare UUID다. 아래 목록 외 동적 path 또는 method는 허용하지 않는다.

| Logical Service | Methods and Paths |
| --- | --- |
| Core | `GET|POST /api/v1/spaces`, `GET|POST /api/v1/spaces/{spaceId}/meetings`, `GET /api/v1/spaces/{spaceId}/members`, `PATCH|DELETE /api/v1/spaces/{spaceId}/members/{memberId}`, `POST /api/v1/spaces/{spaceId}/owner-transfer`, `GET /api/v1/spaces/{spaceId}/project-ai/context-candidates` |
| Core | `GET|PATCH|DELETE /api/v1/meetings/{meetingId}`, `GET|POST /api/v1/meetings/{meetingId}/participants`, `PATCH /api/v1/meetings/{meetingId}/participants/{participantId}`, `GET /api/v1/meetings/{meetingId}/join-requests`, `POST /api/v1/meetings/join-requests`, `POST /api/v1/meetings/{meetingId}/join-requests/{requestId}/approve|reject`, `GET /api/v1/meetings/{meetingId}/dialogue` |
| Core | `GET /api/v1/meetings/{meetingId}/task-candidates`, `POST /api/v1/meetings/{meetingId}/task-candidates/{candidateId}/confirm`, `POST /api/v1/meetings/{meetingId}/reports/{reportId}/confirm` |
| AI | `POST /api/v1/spaces/{spaceId}/ai/chat`, `POST /api/v1/meetings/{meetingId}/ai/chat`, `POST /api/v1/meetings/{meetingId}/reports/generate`, `POST /api/v1/meetings/{meetingId}/task-candidates/generate` |
| LiveKit | `POST /api/v1/meetings/{meetingId}/livekit-token`, `POST /api/v1/meetings/{meetingId}/transcription/start`, `POST /api/v1/meetings/{meetingId}/transcription/{sessionId}/stop` |

Query parameter는 허용된 route의 현재 Backend 계약에만 전달하며 목적지 선택에 사용하지 않는다.

## Header and Response Rules

- Request allowlist: `Content-Type`, `Accept`, BFF가 생성한 `Authorization`.
- Response allowlist: `Content-Type`, `Cache-Control`, `ETag`.
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
