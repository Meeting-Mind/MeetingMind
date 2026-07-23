# Rollout Runbook: Browser to Web BFF

## Scope

이 runbook은 T023 Browser session cutover의 제한된 rollout/rollback 기준이다. ECS Fargate/ALB/CloudWatch 배포 자체는 Q-013/Q-023과 T041~T052 범위이며, 여기서는 애플리케이션이 제공해야 하는 readiness drain, 저카디널리티 metric과 판단 기준을 고정한다.

## Non-Negotiable Boundaries

- Browser는 모든 단계에서 same-origin BFF만 호출하고 MeetingMind access/refresh token이나 `Authorization: Bearer`를 받지 않는다.
- rollback 대상은 동일 cookie 이름, Spring Session Redis namespace, Token Vault namespace/암호화 계약을 읽을 수 있는 안정 BFF release다.
- current Backend auth/API와 DB는 compatibility adapter의 server-side 대상으론 보존하지만 Browser direct Backend fallback으로 노출하지 않는다.
- rollback 중 Redis session, encrypted Token Bundle, Backend DB나 migration을 삭제·역수정하지 않는다.

## Runtime Controls

| Control | Default | Behavior |
| --- | --- | --- |
| `BFF_ACCEPT_BROWSER_TRAFFIC` | `true` | `false`면 rollout readiness component를 `DOWN`으로 만들어 신규 traffic을 drain한다. liveness는 유지해 원인 조사와 정상 종료를 허용한다. |
| `BFF_AUTH_PROVIDER` | `auth-service` | 신규 인증 발급 대상을 선택한다. `legacy`는 승인된 rollback에서만 사용하며 호출 실패에 따른 자동 fallback은 없다. |
| `BFF_AUTH_ISSUER` | `https://auth.meetingmind.internal` | 새 Token Bundle metadata issuer. legacy rollback은 `meetingmind-core-legacy`를 함께 설정한다. |
| `MEETINGMIND_AUTH_VALIDATION_MODE` | `DUAL` | Core가 `LEGACY_ONLY`, `DUAL`, `TARGET_ONLY` 중 하나로 legacy/target issuer 수용 범위를 결정한다. |

traffic percentage는 ALB와 ECS deployment 구성이 소유한다. 애플리케이션 flag가 임의 사용자 ID, cookie 또는 header로 canary를 선택하지 않는다.

## Auth Service Cutover Order

1. T034 final delta `APPLY`와 `VERIFY`를 통과하고 Auth DB/JWKS/KMS readiness를 확인한다.
2. Core를 `MEETINGMIND_AUTH_VALIDATION_MODE=DUAL`로 먼저 배포해 legacy와 target profile을 결정적으로 구분한다. target 검증 실패는 legacy로 재시도하지 않는다.
3. BFF를 `BFF_AUTH_PROVIDER=auth-service`, target issuer로 배포한다. 기존 직렬화 BffSession/Token Bundle은 추측 변환하지 않고 재로그인을 요구한다.
4. 신규 login/signup에서 실제 AuthSession ID, Auth UUID Redis principal index, Core projection `204`, 서비스별 audience proxy와 refresh/revoke smoke를 확인한다.
5. 7일 guardrail과 legacy 발급 0건을 확인한 뒤 Core를 `TARGET_ONLY`로 전환한다. legacy endpoint/DB 삭제는 별도 승인 뒤에만 수행한다.

## Metrics

metric label에는 path variable, user/session ID, email, token, provider body와 trace raw value를 넣지 않는다.

| Metric | Labels | Use |
| --- | --- | --- |
| `meetingmind.bff.browser.requests` timer | `operation=csrf|signup|login|google|session|logout|protected`, `outcome=success|rejected|client_error|unauthenticated|server_error` | login/logout/session/proxy 성공·오류율과 latency |
| `meetingmind.bff.refresh` counter | `outcome=success|failure` | 실제 refresh leader의 회전 성공률 |
| `meetingmind.bff.session.invalid` counter | none | common error code가 정확히 `SESSION_INVALID`인 최종 세션 폐기 횟수 |
| readiness `rollout` | none | release traffic 수용 여부 |

`login`의 `rejected`는 잘못된 자격 증명 같은 정상 보안 거부이며 기술 오류율에서 제외한다. 미인증 보호 요청은 `unauthenticated`, 실제 최종 `SESSION_INVALID` common error만 별도 counter로 분리한다.

## Staged Cutover

| Stage | Traffic | Minimum Observation | Advance Condition |
| --- | ---: | --- | --- |
| Canary | 5% | 30분, browser auth 요청 100건 이상 | 즉시 중단 조건 없음, 아래 비율 기준 통과 |
| Limited | 25% | 60분 | 2개 연속 5분 window 통과 |
| Half | 50% | 120분 | 2개 연속 5분 window 통과 |
| Full | 100% | 7일 rollback window | guardrail 위반 없음과 보안 검토 승인 |

운영 traffic이 최소 표본에 못 미치면 시간을 늘리고 표본 없이 다음 단계로 자동 승격하지 않는다.

## Guardrails

다음 비율 기준은 최종 SLO가 아니라 Phase 2 rollout 중단 기준이다. Q-013의 운영 SLO를 대신하지 않는다.

- 즉시 중단: Browser/응답/로그/metric에서 MeetingMind token 원문 발견, CSRF 우회, 잘못된 사용자/회의 권한 허용, logout 뒤 이전 cookie 재사용 성공.
- 즉시 중단: 새 release의 rollout/readiness `DOWN`, Redis/Vault 복호화 또는 session namespace 비호환.
- 2개 연속 5분 window에서 전체 `server_error >= 1%`이면 rollback한다.
- login/signup/google의 `server_error >= 1%`이면 rollback한다. `rejected`는 분모와 경보에서 분리한다.
- refresh 표본 100건 이상에서 `failure >= 0.5%` 또는 표본과 무관하게 연속 5회 실패하면 rollback한다.
- logout의 `server_error >= 1%` 또는 검증된 session/cookie 정리 실패 1건이면 rollback한다.
- 보호 API 표본 100건 이상에서 `meetingmind.bff.session.invalid / protected requests >= 2%`이면 refresh/session 회귀로 간주해 rollback한다.

## Rollback Procedure

1. 신규 release에 `BFF_ACCEPT_BROWSER_TRAFFIC=false`를 적용해 readiness를 `DOWN`으로 만들고 신규 요청을 drain한다.
2. ALB traffic과 ECS Service를 마지막 안정 BFF Task Definition revision 100%, 신규 revision 0%로 복원한다.
3. 안정 release의 liveness/readiness, login→session bootstrap→보호 API→logout smoke를 확인한다.
4. Redis session과 encrypted Token Vault namespace/key/schema를 그대로 보존하고 cleanup 또는 migration rollback을 실행하지 않는다.
5. current Backend compatibility API/DB 상태와 refresh/logout 결과를 확인한다.
6. incident ID, 최초 위반 metric/window, 영향 traffic 비율, token/PII redaction 확인과 재출시 조건을 기록한다.

Auth Service cutover rollback은 Core를 `DUAL`로 유지한 상태에서 신규 BFF를 drain하고 `BFF_AUTH_PROVIDER=legacy`, `BFF_AUTH_ISSUER=meetingmind-core-legacy`로 신규 발급을 되돌린다. 이미 발급된 target BffSession은 provider 간 refresh token을 혼용하지 않고 만료/강제 재로그인한다. Auth/Core DB migration은 역수정하거나 삭제하지 않는다.

Frontend direct Backend, Browser token storage/Bearer 재도입은 이 runbook의 rollback 수단이 아니다. 필요하다고 판단되면 별도 보안 사건과 승인된 새 spec으로 다룬다.

## Rollback Window Closure

Full 100% traffic에서 7일 동안 guardrail 위반이 없고 안정 BFF artifact, Backend compatibility API/DB와 Redis/Vault backup을 확인한 뒤 Phase 2 rollback window를 종료할 수 있다. window 종료는 Auth Service 추출 승인을 자동으로 의미하지 않으며 T030 계약과 T031~T035 구현·migration gate를 별도로 충족해야 한다.
