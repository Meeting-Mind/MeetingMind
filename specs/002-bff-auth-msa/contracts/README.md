# BFF/Auth Contract Index

| Contract | Trust Boundary | Status | Notes |
| --- | --- | --- | --- |
| `browser-auth-api.md` | Browser ↔ Web BFF | Target, implemented for current-session flow | 브라우저에 access/refresh를 반환하지 않는다. |
| `auth-service-api.md` | Web BFF ↔ Auth Service | Implemented through T035 | 자격/refresh/revoke, KMS signing/JWKS와 BFF target client/Core dual-validation cutover가 구현됐다. |
| `core-user-projection-api.md` | Web BFF → Core | Implemented T035 | Auth 성공 뒤 deterministic resource User projection을 동기 멱등 생성하고 성공 후에만 Browser session을 만든다. |
| `auth-revocation-event.md` | Auth Service → BFF/Resource Services | Target, T032 producer-complete | revoke와 unpublished outbox 원자 기록은 구현됐고 transport publisher/consumer는 T045 출시 gate다. |
| `bff-proxy-routes.md` | Browser ↔ Web BFF ↔ current Backend | Phase 1 compatibility | 허용 method/path, 논리 서비스 분류와 장애 응답을 고정한다. |
| `service-call-boundaries.md` | Browser/BFF/Auth/Core/STT/AI/Provider | Current NonProd runtime | 실제 caller, 목적지, workload identity, JWT audience와 데이터 소유권을 한 표로 고정한다. |
| `specs/001-meetingmind-core/contracts/auth-api.md` | Browser ↔ current Backend | Current legacy compatibility | Phase 1 BFF adapter와 rollback에만 사용한다. |

모든 외부 브라우저 경로는 `/api/v1/*`를 유지한다. 내부 endpoint는 public ALB에 공개하지 않고 workload 인증과 서비스별 Security Group을 함께 적용한다.
