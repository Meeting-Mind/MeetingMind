# ERD: BFF Auth and Gradual MSA

이 ERD는 저장소 소유권을 포함한 목표 논리 모델이다. T030에서 AuthSession별 refresh family, revoke outbox와 audience별 Token Bundle을 확정했다.

```mermaid
erDiagram
  USER ||--o{ AUTH_IDENTITY : authenticates_with
  USER ||--o{ AUTH_SESSION : owns
  USER ||--o| CORE_USER_PROJECTION : projected_as
  AUTH_SESSION ||--o{ AUTH_REFRESH_CREDENTIAL : rotates
  AUTH_SESSION ||--o{ BFF_SESSION : backs
  BFF_SESSION ||--|| TOKEN_BUNDLE : references
  USER ||--o{ SESSION_AUDIT : generates
  AUTH_SESSION ||--o{ SESSION_AUDIT : records
  AUTH_SESSION ||--o{ AUTH_OUTBOX_EVENT : emits

  USER {
    uuid id PK
    string email UK
    string displayName
    string pictureUrl
    string status
    datetime createdAt
    datetime updatedAt
    datetime lastLoginAt
  }

  AUTH_IDENTITY {
    uuid id PK
    uuid userId FK
    string provider
    string providerUserId
    string passwordHash
    datetime createdAt
    datetime lastUsedAt
  }

  CORE_USER_PROJECTION {
    string id PK
    uuid authUserId UK
  }

  AUTH_SESSION {
    uuid id PK
    uuid userId FK
    datetime createdAt
    datetime lastRotatedAt
    uuid refreshFamilyId UK
    datetime expiresAt
    datetime revokedAt
    string revokeReason
    string deviceLabel
    string lastIpPrefix
  }

  AUTH_REFRESH_CREDENTIAL {
    uuid id PK
    uuid authSessionId FK
    string tokenHash UK
    datetime issuedAt
    datetime expiresAt
    datetime usedAt
    datetime revokedAt
    uuid replacementId
    uuid familyId
  }

  BFF_SESSION {
    string id PK
    string resourceUserId
    uuid authUserId
    uuid authSessionId
    uuid tokenBundleId UK
    datetime createdAt
    datetime lastAccessedAt
    datetime idleExpiresAt
    datetime absoluteExpiresAt
    boolean rememberMe
    datetime authenticatedAt
    string status
  }

  TOKEN_BUNDLE {
    uuid id PK
    uuid authSessionId
    bytes encryptedPayload
    bytes encryptedDataKey
    string keyId
    json accessExpiresAtByAudience
    datetime refreshExpiresAt
    string issuer
    json audiences
    json scopesByAudience
    long version
    int schemaVersion
    datetime createdAt
    datetime updatedAt
  }

  SESSION_AUDIT {
    uuid id PK
    uuid userId
    uuid authSessionId
    string bffSessionIdHash
    string eventType
    string reasonCode
    datetime occurredAt
    string traceId
    json metadata
  }

  AUTH_OUTBOX_EVENT {
    uuid id PK
    string aggregateType
    uuid aggregateId
    string eventType
    int eventVersion
    json payload
    datetime createdAt
    datetime publishedAt
    int attemptCount
    string lastErrorCode
  }
```

## Physical Ownership

```mermaid
flowchart LR
  Redis["BFF Session Redis\nBFF_SESSION"]
  Vault["BFF Token Vault\nTOKEN_BUNDLE ciphertext"]
  AuthDb["Auth PostgreSQL\nUSER / AUTH_IDENTITY / AUTH_SESSION\nAUTH_REFRESH_CREDENTIAL / SESSION_AUDIT / AUTH_OUTBOX_EVENT"]
  ResourceDb["Resource-owned DB\nCORE_USER_PROJECTION / Space / Meeting / ACL"]

  Redis -. "opaque references only" .-> Vault
  Redis -. "authSessionId/authUserId only" .-> AuthDb
  AuthDb -. "API or event projection" .-> ResourceDb
```

- 점선은 물리 foreign key나 cross-database join이 아니다.
- BFF는 Auth DB를 직접 읽지 않고 내부 API를 사용한다.
- Resource Service는 Auth DB나 Redis를 직접 읽지 않는다.
- `USER`와 `CORE_USER_PROJECTION` 관계는 UUID 값의 논리 projection이며 물리 cross-DB FK가 아니다. Core의 기존 문자열 PK/FK는 유지한다.
- BFF_SESSION은 Browser/Core에 노출하는 `resourceUserId`와 내부 JWT/Auth index용 `authUserId`를 분리한다. 두 값은 `resourceUserId = "user-" + authUserId` 불변식을 가진다.
- 현재 Core Prototype ERD의 `AUTH_SESSION.refreshTokenHash`는 legacy schema이며 이 목표 ERD로 점진 대체한다.
- T031 물리 table은 각각 `auth_users`, `auth_identities`, `auth_sessions`, `auth_refresh_credentials`, `session_audits`, `auth_outbox_events`다. Auth runtime role은 업무 table의 `SELECT/INSERT/UPDATE`, 감사 table의 `SELECT/INSERT`만 수행하고 `DELETE`, schema/Flyway history를 소유하지 않는다.
