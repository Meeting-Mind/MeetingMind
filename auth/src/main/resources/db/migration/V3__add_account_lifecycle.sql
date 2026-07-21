alter table auth_users
    add column disabled_at timestamptz,
    add column withdrawal_requested_at timestamptz;

create table auth_password_history (
    id uuid primary key,
    user_id uuid not null references auth_users(id),
    password_hash varchar(255) not null,
    created_at timestamptz not null default now(),
    constraint auth_password_history_hash_not_blank check (length(btrim(password_hash)) > 0)
);

create index auth_password_history_user_created_idx
    on auth_password_history (user_id, created_at desc, id desc);

create table auth_password_reset_tokens (
    id uuid primary key,
    user_id uuid not null references auth_users(id),
    token_hash varchar(255) not null unique,
    request_ip_prefix varchar(64),
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    used_at timestamptz,
    constraint auth_password_reset_tokens_expiry_check check (expires_at > created_at),
    constraint auth_password_reset_tokens_used_after_create_check
        check (used_at is null or used_at >= created_at)
);

create index auth_password_reset_tokens_user_created_idx
    on auth_password_reset_tokens (user_id, created_at desc);

create index auth_password_reset_tokens_ip_created_idx
    on auth_password_reset_tokens (request_ip_prefix, created_at desc)
    where request_ip_prefix is not null;

alter table auth_sessions
    drop constraint auth_sessions_revoke_reason_check;

alter table auth_sessions
    add constraint auth_sessions_revoke_reason_check
        check (
            revoke_reason is null
            or revoke_reason in (
                'CURRENT_LOGOUT',
                'ALL_DEVICE_LOGOUT',
                'USER_DISABLED',
                'REFRESH_REUSE',
                'ADMIN_REVOKE',
                'EXPIRED',
                'PASSWORD_CHANGED',
                'PASSWORD_RESET',
                'ACCOUNT_WITHDRAWAL'
            )
        );

grant select, insert, update
    on auth_password_history,
       auth_password_reset_tokens
    to meetingmind_auth_app;
