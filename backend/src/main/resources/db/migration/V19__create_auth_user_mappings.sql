create table auth_user_mappings (
    auth_user_id uuid primary key,
    core_user_id varchar(64) not null unique references users(id),
    source varchar(32) not null,
    source_version bigint not null default 1,
    mapped_at timestamptz not null default now(),
    constraint auth_user_mappings_source_check
        check (source in ('AUTH_PROJECTION', 'LEGACY_MANIFEST', 'MANUAL_RECONCILIATION')),
    constraint auth_user_mappings_source_version_check check (source_version > 0)
);

comment on table auth_user_mappings is
    'Immutable projection from Auth Service UUID to legacy Core users.id. No cross-database foreign key.';

create index ix_auth_user_mappings_core_user_id on auth_user_mappings (core_user_id);
