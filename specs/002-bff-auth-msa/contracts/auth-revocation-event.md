# Auth Revocation Event Contract

## Document Status

| Field | Value |
| --- | --- |
| Status | Target Internal Contract; T032 transactional producer-complete, transport/consumers pending T045 |
| Producer | Auth Service transactional outbox |
| Consumers | Web BFF, Core, AI, LiveKit adapter and future Resource Services |
| Delivery | At-least-once, partition/ordering key `authSessionId` |

## Purpose

Access JWT는 10분 동안 Resource Service가 Auth Service 동기 조회 없이 로컬 검증한다. 현재 로그아웃, 모든 기기 로그아웃, refresh 재사용, 사용자 비활성화 시 아직 만료되지 않은 access를 빠르게 차단하기 위해 Auth Service가 `sid` 폐기를 durable event로 전파한다.

이 계약은 중앙 revocation store를 매 요청 조회하지 않는다. event 처리 지연이나 consumer 장애 시 남는 최대 유효 시간은 access TTL 10분과 검증 skew 최대 60초이며 운영 지표와 경보로 추적한다.

## AuthSessionRevokedV1

```json
{
  "eventId": "018f40d8-7a8d-7b2e-8d90-a4f59451dcf5",
  "eventType": "AUTH_SESSION_REVOKED",
  "eventVersion": 1,
  "occurredAt": "2026-07-17T02:30:00Z",
  "userId": "0a5b7c1e-5d75-4dc0-a10e-a330d0583930",
  "authSessionId": "e655a7be-39b1-44eb-9559-419ea96e5c62",
  "reason": "CURRENT_LOGOUT",
  "denyUntil": "2026-07-17T02:40:00Z",
  "traceId": "01J2..."
}
```

### Field Rules

- `eventId`: 전역 유일 식별자. consumer idempotency key로 사용한다.
- `authSessionId`: JWT `sid`와 동일한 논리 AuthSession UUID다.
- `reason`: `CURRENT_LOGOUT`, `ALL_DEVICE_LOGOUT`, `USER_DISABLED`, `REFRESH_REUSE`, `ADMIN_REVOKE`, `EXPIRED` 중 하나다.
- `denyUntil`: 폐기 시점에 존재할 수 있는 access의 최대 만료 시점이다. 기본은 `occurredAt + 10분 + 60초 clock skew`이며 더 늦은 실제 발급 만료가 있으면 그 값을 사용한다.
- token, refresh hash, email, IP, User-Agent와 업무 권한은 event에 넣지 않는다.

## Producer Semantics

- AuthSession revoke와 outbox insert를 같은 Auth PostgreSQL 트랜잭션으로 커밋한다.
- 현재 로그아웃과 refresh reuse는 해당 AuthSession event 하나를 만든다.
- 모든 기기 로그아웃, 사용자 비활성화와 관리자 전체 폐기는 revoke된 AuthSession마다 event 하나를 만든다.
- API 성공은 revoke와 outbox가 durable하게 기록됐음을 의미하며 모든 consumer 처리 완료를 의미하지 않는다.
- outbox publisher는 성공한 publish만 전송 완료로 표시하고 token/credential을 log나 tracing에 남기지 않는다.

## Consumer Semantics

- consumer는 `eventId` 기준으로 중복을 허용하는 idempotent 처리를 한다.
- `sid=authSessionId`를 `denyUntil`까지 로컬 denylist에 보관하고 만료 후 제거한다.
- 요청 JWT의 `sid`가 denylist에 있으면 signature와 시간이 유효해도 `401`로 거부한다.
- BFF는 연결된 BffSession과 Token Bundle을 사용자/session 역색인으로 정리한다. 이미 삭제된 경우 멱등 성공한다.
- event 순서가 뒤바뀌어도 같은 `sid`의 가장 늦은 `denyUntil`을 유지한다.
- 알 수 없는 version, 필수 필드 누락과 처리 실패는 ack하지 않고 dead-letter/재시도 및 보안 경보 대상으로 남긴다.

## Security and Observability

- event transport와 consumer endpoint는 mTLS SPIFFE workload identity와 producer/consumer allowlist를 적용한다.
- event payload와 metric label에는 사용자 표시 정보, token, credential과 raw exception을 넣지 않는다.
- 최소 지표는 outbox backlog age, publish failure, consumer lag, processing failure, denylist size와 revoke-to-apply latency다.
- `revoke-to-apply`가 access TTL 10분에 근접하면 출시를 차단하고 consumer 복구 또는 영향 서비스 traffic drain을 수행한다.

## Implementation Boundary

T032는 이 계약의 unpublished outbox row를 AuthSession revoke와 원자 기록한다. 메시지 제품, publisher의 publish 완료 갱신·재시도·경보와 consumer 구현은 T045에서 선택한다. 어떤 제품을 선택해도 이 payload, at-least-once/idempotency, Auth DB transaction과 10분 TTL+60초 skew의 bounded-risk 의미를 바꾸지 않는다.
