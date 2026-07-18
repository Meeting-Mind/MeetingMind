# ERD: BFF Auth and Gradual MSA

이 ERD는 저장소 소유권을 포함한 목표 논리 모델이다. T030에서 AuthSession별 refresh family, revoke outbox와 audience별 Token Bundle을 확정했다.

```mermaid
erDiagram
  USER ||--o{ AUTH_IDENTITY : authenticates_with
  USER ||--o{ AUTH_SESSION : owns
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
    uuid userId
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
  ResourceDb["Resource-owned DB\nSpace / Meeting / ACL"]

  Redis -. "opaque references only" .-> Vault
  Redis -. "authSessionId/userId only" .-> AuthDb
  AuthDb -. "API or event projection" .-> ResourceDb
```

- 점선은 물리 foreign key나 cross-database join이 아니다.
- BFF는 Auth DB를 직접 읽지 않고 내부 API를 사용한다.
- Resource Service는 Auth DB나 Redis를 직접 읽지 않는다.
- 현재 Core Prototype ERD의 `AUTH_SESSION.refreshTokenHash`는 legacy schema이며 이 목표 ERD로 점진 대체한다.
- T031 물리 table은 각각 `auth_users`, `auth_identities`, `auth_sessions`, `auth_refresh_credentials`, `session_audits`, `auth_outbox_events`다. Auth runtime role은 업무 table의 `SELECT/INSERT/UPDATE`, 감사 table의 `SELECT/INSERT`만 수행하고 `DELETE`, schema/Flyway history를 소유하지 않는다.
