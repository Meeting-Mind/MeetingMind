alter table meetings
    add column join_code_hash varchar(128);

alter table meetings
    add constraint meetings_join_code_hash_not_blank check (
        join_code_hash is null or length(trim(join_code_hash)) > 0
    );

create unique index ux_meetings_join_code_hash
    on meetings (join_code_hash)
    where join_code_hash is not null;

create table meeting_join_requests (
    id varchar(64) primary key,
    meeting_id varchar(64) not null references meetings(id),
    user_id varchar(64) not null references users(id),
    status varchar(32) not null default 'PENDING',
    requested_at timestamptz not null default now(),
    reviewed_at timestamptz,
    reviewed_by varchar(64) references users(id),
    constraint meeting_join_requests_status_check check (
        status in ('PENDING', 'APPROVED', 'REJECTED')
    ),
    constraint meeting_join_requests_resolution_check check (
        (status = 'PENDING' and reviewed_at is null and reviewed_by is null)
        or (status in ('APPROVED', 'REJECTED') and reviewed_at is not null and reviewed_by is not null)
    )
);

create unique index ux_meeting_join_requests_pending_user
    on meeting_join_requests (meeting_id, user_id)
    where status = 'PENDING';

create index ix_meeting_join_requests_meeting_status
    on meeting_join_requests (meeting_id, status, requested_at);

create index ix_meeting_join_requests_user
    on meeting_join_requests (user_id, requested_at desc);

comment on table meeting_invitations is
    'Superseded by meeting_join_requests; retained because V7 is an immutable shared migration.';
