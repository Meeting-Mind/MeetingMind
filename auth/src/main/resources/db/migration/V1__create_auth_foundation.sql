revoke create on schema public from public;

create table auth_users (
    id uuid primary key,
    email varchar(320) not null,
    display_name varchar(200) not null,
    picture_url text,
    status varchar(20) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    last_login_at timestamptz,
    constraint auth_users_email_unique unique (email),
    constraint auth_users_email_canonical_check
        check (email = lower(btrim(email)) and length(email) between 3 and 320),
    constraint auth_users_display_name_check
        check (length(btrim(display_name)) between 1 and 200),
    constraint auth_users_status_check
        check (status in ('ACTIVE', 'DISABLED'))
);

create table auth_identities (
    id uuid primary key,
    user_id uuid not null references auth_users(id),
    provider varchar(20) not null,
    provider_user_id varchar(320) not null,
    password_hash varchar(255),
    created_at timestamptz not null default now(),
    last_used_at timestamptz,
    constraint auth_identities_provider_check
        check (provider in ('LOCAL', 'GOOGLE')),
    constraint auth_identities_provider_user_unique
        unique (provider, provider_user_id),
    constraint auth_identities_user_provider_unique
        unique (user_id, provider),
    constraint auth_identities_password_boundary_check
        check (
            (provider = 'LOCAL' and password_hash is not null)
            or (provider = 'GOOGLE' and password_hash is null)
        )
);

create table auth_sessions (
    id uuid primary key,
    user_id uuid not null references auth_users(id),
    refresh_family_id uuid not null,
    created_at timestamptz not null default now(),
    last_rotated_at timestamptz not null default now(),
    expires_at timestamptz not null,
    revoked_at timestamptz,
    revoke_reason varchar(32),
    device_label varchar(256),
    last_ip_prefix varchar(64),
    constraint auth_sessions_refresh_family_unique unique (refresh_family_id),
    constraint auth_sessions_id_family_unique unique (id, refresh_family_id),
    constraint auth_sessions_expiry_check check (expires_at > created_at),
    constraint auth_sessions_rotation_check check (last_rotated_at >= created_at),
    constraint auth_sessions_revoke_pair_check
        check (
            (revoked_at is null and revoke_reason is null)
            or (revoked_at is not null and revoke_reason is not null)
        ),
    constraint auth_sessions_revoke_reason_check
        check (
            revoke_reason is null
            or revoke_reason in (
                'CURRENT_LOGOUT',
                'ALL_DEVICE_LOGOUT',
                'USER_DISABLED',
                'REFRESH_REUSE',
                'ADMIN_REVOKE',
                'EXPIRED'
            )
        )
);

create table auth_refresh_credentials (
    id uuid primary key,
    auth_session_id uuid not null,
    family_id uuid not null,
    token_hash varchar(255) not null,
    issued_at timestamptz not null default now(),
    expires_at timestamptz not null,
    used_at timestamptz,
    revoked_at timestamptz,
    replacement_id uuid,
    constraint auth_refresh_credentials_token_hash_unique unique (token_hash),
    constraint auth_refresh_credentials_id_family_unique unique (id, family_id),
    constraint auth_refresh_credentials_replacement_unique unique (replacement_id),
    constraint auth_refresh_credentials_session_family_fk
        foreign key (auth_session_id, family_id)
        references auth_sessions(id, refresh_family_id),
    constraint auth_refresh_credentials_replacement_family_fk
        foreign key (replacement_id, family_id)
        references auth_refresh_credentials(id, family_id),
    constraint auth_refresh_credentials_expiry_check check (expires_at > issued_at),
    constraint auth_refresh_credentials_used_replacement_check
        check (
            (used_at is null and replacement_id is null)
            or (used_at is not null and replacement_id is not null)
        )
);

create unique index auth_refresh_credentials_one_active_leaf
    on auth_refresh_credentials(auth_session_id)
    where used_at is null and revoked_at is null;

create index auth_sessions_user_active_idx
    on auth_sessions(user_id, expires_at)
    where revoked_at is null;

create index auth_refresh_credentials_family_idx
    on auth_refresh_credentials(family_id, issued_at);

create table session_audits (
    id uuid primary key,
    user_id uuid references auth_users(id),
    auth_session_id uuid references auth_sessions(id),
    bff_session_id_hash varchar(128),
    event_type varchar(64) not null,
    reason_code varchar(64),
    occurred_at timestamptz not null default now(),
    trace_id varchar(128),
    metadata jsonb not null default '{}'::jsonb,
    constraint session_audits_metadata_object_check
        check (jsonb_typeof(metadata) = 'object')
);

create index session_audits_user_time_idx
    on session_audits(user_id, occurred_at desc);

create index session_audits_session_time_idx
    on session_audits(auth_session_id, occurred_at desc);

create table auth_outbox_events (
    id uuid primary key,
    aggregate_type varchar(32) not null,
    aggregate_id uuid not null references auth_sessions(id),
    event_type varchar(64) not null,
    event_version integer not null,
    payload jsonb not null,
    created_at timestamptz not null default now(),
    published_at timestamptz,
    attempt_count integer not null default 0,
    last_error_code varchar(64),
    constraint auth_outbox_events_aggregate_type_check
        check (aggregate_type = 'AUTH_SESSION'),
    constraint auth_outbox_events_event_type_check
        check (event_type = 'AUTH_SESSION_REVOKED'),
    constraint auth_outbox_events_event_version_check
        check (event_version = 1),
    constraint auth_outbox_events_payload_object_check
        check (jsonb_typeof(payload) = 'object'),
    constraint auth_outbox_events_attempt_count_check
        check (attempt_count >= 0)
);

create index auth_outbox_events_unpublished_idx
    on auth_outbox_events(created_at)
    where published_at is null;

grant usage on schema public to meetingmind_auth_app;
grant select, insert, update, delete
    on auth_users,
       auth_identities,
       auth_sessions,
       auth_refresh_credentials,
       session_audits,
       auth_outbox_events
    to meetingmind_auth_app;

revoke all on table flyway_schema_history from meetingmind_auth_app;
revoke create on schema public from meetingmind_auth_app;

alter default privileges in schema public
    grant select, insert, update, delete on tables to meetingmind_auth_app;
