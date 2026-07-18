# Auth Data Migration Runbook

## Scope

T034는 legacy Core DB의 인증 identity가 연결된 User/AuthIdentity만 Auth DB로 복사한다. Core `users.id`와 업무 FK는 유지하고 `users.auth_user_id`로 Auth UUID subject를 연결한다.

다음 데이터는 이전하지 않는다.

- legacy `auth_sessions`
- legacy refresh hash 또는 활성 BFF Token Bundle
- password/Google credential/refresh 원문

기존 refresh hash에는 Auth Service의 HMAC secret, 1회용 credential lineage와 안정된 AuthSession ID가 없으므로 추정 변환하지 않는다. 실제 BFF→Auth 전환과 기존 세션 만료·재로그인은 T035에서 수행한다.

## Preconditions

1. Core V13과 Auth V1/V2 migration이 적용돼 있어야 한다.
2. source/target backup과 복구 절차를 확인한다.
3. source는 인증 데이터 read 권한, target은 migration 전용 `SELECT/INSERT/UPDATE/TEMP` 권한을 사용한다. application runtime credential을 사용하지 않는다.
4. DB URL과 credential은 Secret Manager 또는 실행 환경으로만 주입하고 shell history, CI log, PR에 남기지 않는다.
5. `DRY_RUN`에서 `INVALID_*`, `*_PROJECTION_MISMATCH`, `*_OWNERSHIP_CONFLICT`가 하나라도 나오면 source 데이터를 추측 수정하거나 임의 UUID로 치환하지 않고 전환을 중단한다.
6. 최초 snapshot 소요 시간이 승인된 최종 인증 쓰기 중단 시간보다 길면 `APPLY`를 강행하지 않고 CDC/dual-write를 별도 설계한다.

## Configuration

```bash
export AUTH_MIGRATION_SOURCE_URL='jdbc:postgresql://CORE_HOST:5432/meetingmind'
export AUTH_MIGRATION_SOURCE_USER='auth_migration_reader'
export AUTH_MIGRATION_SOURCE_PASSWORD='...'
export AUTH_MIGRATION_TARGET_URL='jdbc:postgresql://AUTH_HOST:5432/meetingmind_auth'
export AUTH_MIGRATION_TARGET_USER='auth_migration_writer'
export AUTH_MIGRATION_TARGET_PASSWORD='...'
```

도구는 두 JDBC 연결을 별도로 열며 DB link, foreign data wrapper 또는 cross-DB SQL을 만들지 않는다. 실행 결과에는 mode와 건수, mismatch 건수, 고정 오류 코드만 출력하며 credential과 인증 원문을 출력하지 않는다.

## Initial Snapshot

```bash
cd auth
AUTH_DATA_MIGRATION_MODE=DRY_RUN ./gradlew migrateLegacyAuthData
AUTH_DATA_MIGRATION_MODE=APPLY ./gradlew migrateLegacyAuthData
AUTH_DATA_MIGRATION_MODE=VERIFY ./gradlew migrateLegacyAuthData
```

- `DRY_RUN`: source mapping과 target ownership 충돌을 검사한다. 아직 없는 target row는 mismatch로 집계될 수 있다.
- `APPLY`: 한 target transaction에서 User를 먼저, AuthIdentity를 다음에 멱등 upsert하고 모든 필드를 exact reconciliation한다.
- `VERIFY`: target을 변경하지 않고 exact reconciliation한다. 성공 조건은 `mismatches=0`이다.

동일 snapshot에 대한 `APPLY` 재실행은 같은 User/Identity ID를 유지하며 중복 row를 만들지 않아야 한다.

## Final Delta and Cutover Gate

1. BFF의 legacy `/signup`, `/login`, `/google`, `/refresh` 신규 요청을 drain해 인증 쓰기를 중단한다.
2. 진행 중인 요청이 끝났는지 확인하고 final `DRY_RUN`을 실행한다.
3. final `APPLY`를 실행한다.
4. 바로 `VERIFY`를 실행해 `mismatches=0`과 source User/AuthIdentity 건수를 기록한다.
5. local identity의 BCrypt hash와 Google provider subject가 target에 존재하는지 표본이 아닌 자동 대사 결과로 확인한다.
6. T035의 BFF→Auth 발급 전환 전까지 legacy issuer를 source of truth로 유지한다.

T034 성공만으로 target issuer를 활성화하거나 기존 BFF session을 삭제하지 않는다. 실제 issuer/BFF/Core 전환은 T035 완료조건을 별도로 통과해야 한다.

## Failure and Rollback

- `DRY_RUN/APPLY/VERIFY` 실패: target transaction은 rollback된다. legacy 인증 쓰기를 다시 허용하고 오류 코드를 조사한다.
- final 대사 불일치: Auth 발급을 시작하지 않는다. legacy DB/issuer와 Browser-BFF 계약을 유지한다.
- Core V13은 rollback SQL로 되돌리지 않는다. nullable projection과 기존 문자열 FK는 legacy 경로에 영향을 주지 않는다.
- 이미 적재된 target User/AuthIdentity는 임의 삭제하지 않는다. 재실행 가능한 동일 source로 수렴시키거나 별도 승인된 정리 migration을 사용한다.
- BFF/Auth cutover 이후 rollback은 T035의 dual issuer/traffic window를 따른다. Browser token 저장 방식은 복원하지 않는다.

## Evidence to Retain

- 실행 시각, 배포/DB migration version
- `DRY_RUN`, `APPLY`, `VERIFY`의 User/Identity/mismatch 건수와 성공·오류 코드
- 인증 쓰기 drain 시작/종료 시각
- T035 진행 또는 중단 승인자

DB password, password hash, Google subject, token과 전체 row dump는 증적에 포함하지 않는다.
