update meetings
set retention_policy = 'DAYS_30'
where retention_policy is null;

alter table meetings
    alter column retention_policy set default 'DAYS_30',
    alter column retention_policy set not null,
    add constraint meetings_retention_policy_check check (
        retention_policy in ('DAYS_7', 'DAYS_30', 'PERMANENT')
    );

create table meeting_transcripts (
    meeting_id varchar(64) primary key references meetings(id),
    status varchar(32) not null default 'PENDING',
    provider varchar(64),
    language varchar(35),
    started_at timestamptz,
    completed_at timestamptz,
    failure_reason text,
    retention_until timestamptz,
    legal_hold boolean not null default false,
    purged_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint meeting_transcripts_status_check check (
        status in ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')
    ),
    constraint meeting_transcripts_provider_not_blank check (
        provider is null or length(trim(provider)) > 0
    ),
    constraint meeting_transcripts_language_not_blank check (
        language is null or length(trim(language)) > 0
    ),
    constraint meeting_transcripts_lifecycle_check check (
        (status in ('PENDING', 'PROCESSING') and completed_at is null and failure_reason is null)
        or (status = 'COMPLETED' and completed_at is not null and failure_reason is null)
        or (
            status = 'FAILED'
            and completed_at is not null
            and failure_reason is not null
            and length(trim(failure_reason)) > 0
        )
    ),
    constraint meeting_transcripts_started_at_check check (
        started_at is null or started_at >= created_at
    ),
    constraint meeting_transcripts_completed_at_check check (
        completed_at is null or started_at is null or completed_at >= started_at
    ),
    constraint meeting_transcripts_purged_at_check check (
        purged_at is null or completed_at is null or purged_at >= completed_at
    )
);

create index ix_meeting_transcripts_status
    on meeting_transcripts (status, updated_at);

create index ix_meeting_transcripts_retention_due
    on meeting_transcripts (retention_until)
    where retention_until is not null and legal_hold = false and purged_at is null;

create table domain_terms (
    id varchar(64) primary key,
    space_id varchar(64) not null references spaces(id),
    term varchar(200) not null,
    definition text not null,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    archived_at timestamptz,
    constraint domain_terms_term_not_blank check (length(trim(term)) > 0),
    constraint domain_terms_definition_not_blank check (length(trim(definition)) > 0),
    constraint domain_terms_status_check check (status in ('ACTIVE', 'ARCHIVED')),
    constraint domain_terms_archived_at_check check (
        (status = 'ACTIVE' and archived_at is null)
        or (status = 'ARCHIVED' and archived_at is not null)
    )
);

create unique index ux_domain_terms_active_term
    on domain_terms (space_id, lower(term))
    where status = 'ACTIVE';

create index ix_domain_terms_space_status
    on domain_terms (space_id, status);

create table audit_logs (
    id varchar(64) primary key,
    space_id varchar(64) not null references spaces(id),
    actor_user_id varchar(64) references users(id),
    action varchar(64) not null,
    target_type varchar(64) not null,
    target_id varchar(64) not null,
    before_value jsonb,
    after_value jsonb,
    occurred_at timestamptz not null default now(),
    constraint audit_logs_action_not_blank check (length(trim(action)) > 0),
    constraint audit_logs_target_type_not_blank check (length(trim(target_type)) > 0),
    constraint audit_logs_target_id_not_blank check (length(trim(target_id)) > 0),
    constraint audit_logs_before_value_object check (
        before_value is null or jsonb_typeof(before_value) = 'object'
    ),
    constraint audit_logs_after_value_object check (
        after_value is null or jsonb_typeof(after_value) = 'object'
    )
);

create index ix_audit_logs_space_occurred_at
    on audit_logs (space_id, occurred_at desc);

create index ix_audit_logs_actor_occurred_at
    on audit_logs (actor_user_id, occurred_at desc)
    where actor_user_id is not null;

create index ix_audit_logs_target
    on audit_logs (target_type, target_id, occurred_at desc);
