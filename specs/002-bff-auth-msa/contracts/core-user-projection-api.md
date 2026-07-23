# BFF-Core User Projection Internal API Contract

## Document Status

| Field | Value |
| --- | --- |
| Status | Target Internal Contract; T035 implementation |
| Owner | Core Resource Service |
| Base path | `/internal/v1/users` |
| Consumer | Web BFF only |
| Related decisions | Q-020, Q-021, D-014, D-015 |
| Related data model | Auth User, CoreUserProjection, BffSession |

## Boundary Rules

- public ALB에 노출하지 않고 BFF workload identity와 Security Group allowlist를 함께 적용한다.
- 사용자 인증에는 `Authorization: Bearer {meetingmind-core access}`를 사용하고 workload 인증에는 mTLS SPIFFE identity를 사용한다. 둘 중 하나라도 실패하면 거부한다.
- 로컬/CI profile에서만 명시적으로 활성화한 test workload principal header를 허용하며 운영 profile은 이를 무시한다.
- Core는 Auth DB, BFF Redis, Token Vault를 직접 조회하지 않는다.
- API는 projection 생성/갱신만 담당하며 SpaceRole, MeetingParticipant, 업무 권한을 변경하지 않는다.

## POST /internal/v1/users/projection

Auth 성공 직후 Browser session을 만들기 전에 Auth User를 Core resource User로 멱등 projection한다.

### Request

```http
Authorization: Bearer eyJ...
Content-Type: application/json
```

```json
{
  "authUserId": "11111111-1111-4111-8111-111111111111",
  "resourceUserId": "user-11111111-1111-4111-8111-111111111111",
  "email": "miju@meetingmind.ai",
  "displayName": "이미주",
  "pictureUrl": null,
  "status": "ACTIVE"
}
```

### Validation

- access JWT는 target profile `RS256`, `typ=at+jwt`, non-empty `kid`, target issuer, 정확한 `aud=meetingmind-core`를 만족해야 한다.
- JWT `sub` UUID와 `authUserId`가 같아야 한다.
- `resourceUserId`는 정확히 `user-{authUserId}`여야 한다.
- `email`, `displayName`, `pictureUrl`, `status`는 Core User 제약과 입력 길이를 만족해야 한다. `status`는 현재 `ACTIVE`, `DISABLED`만 허용한다.
- 기존 `resourceUserId`가 다른 `authUserId`에 연결됐거나 기존 `authUserId`가 다른 resource User에 연결된 ownership conflict는 갱신하지 않는다.

### Response

- `204 No Content`: 신규 insert, 동일 projection 재요청, 허용된 표시 정보 갱신 모두 멱등 성공.
- `400 INVALID_USER_PROJECTION`: 입력 또는 deterministic ID 규칙 위반.
- `401 UNAUTHORIZED`: access JWT 부재/위조/만료/잘못된 issuer·audience·profile.
- `403 WORKLOAD_FORBIDDEN`: BFF workload identity가 아니거나 workload 인증이 없음.
- `409 USER_PROJECTION_CONFLICT`: resource/Auth ID ownership 충돌.
- `503 USER_PROJECTION_UNAVAILABLE`: Core 저장소 또는 필수 JWKS 의존성 실패.

### Transaction and Retry

- Core는 resource ID/Auth UUID ownership을 검증하고 한 DB transaction으로 insert 또는 허용된 profile 필드를 update한다.
- 같은 payload의 재요청은 부작용 없이 `204`를 반환한다.
- BFF는 성공 뒤에만 BffSession과 Token Bundle을 확정한다.
- 실패 시 BFF는 발급된 `authSessionId`를 best-effort revoke하고 Browser 인증을 `USER_PROJECTION_UNAVAILABLE`로 실패 처리한다. Auth 계정은 삭제하지 않으며 다음 login/signup 재시도에서 같은 projection을 완성할 수 있다.
