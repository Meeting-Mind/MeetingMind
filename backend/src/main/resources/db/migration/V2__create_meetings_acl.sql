create table meetings (
    id varchar(64) primary key,
    space_id varchar(64) not null references spaces(id),
    title varchar(200) not null,
    scheduled_at timestamptz,
    started_at timestamptz,
    ended_at timestamptz,
    status varchar(32) not null default 'SCHEDULED',
    failure_reason text,
    retention_policy varchar(32),
    constraint meetings_title_not_blank check (length(trim(title)) > 0),
    constraint meetings_status_check check (status in ('SCHEDULED', 'IN_PROGRESS', 'ENDED', 'CANCELED'))
);

create index ix_meetings_space_scheduled_at
    on meetings (space_id, scheduled_at);

create index ix_meetings_space_status
    on meetings (space_id, status);

create table meeting_participants (
    id varchar(64) primary key,
    meeting_id varchar(64) not null references meetings(id),
    user_id varchar(64) not null references users(id),
    role varchar(32) not null,
    participant_type varchar(16) not null,
    access_status varchar(32) not null default 'ACTIVE',
    constraint meeting_participants_role_check check (role in ('HOST', 'EDITOR', 'VIEWER')),
    constraint meeting_participants_type_check check (participant_type in ('member', 'guest')),
    constraint meeting_participants_access_status_check check (access_status in ('ACTIVE', 'REVOKED'))
);

create unique index ux_meeting_participants_active_user
    on meeting_participants (meeting_id, user_id)
    where access_status = 'ACTIVE';

create index ix_meeting_participants_user_active
    on meeting_participants (user_id)
    where access_status = 'ACTIVE';

create index ix_meeting_participants_meeting_role_active
    on meeting_participants (meeting_id, role)
    where access_status = 'ACTIVE';

create table meeting_speakers (
    id varchar(64) primary key,
    meeting_id varchar(64) not null references meetings(id),
    label varchar(80) not null,
    display_name varchar(100),
    created_at timestamptz not null default now(),
    constraint meeting_speakers_label_not_blank check (length(trim(label)) > 0),
    constraint meeting_speakers_display_name_not_blank check (
        display_name is null or length(trim(display_name)) > 0
    )
);

create unique index ux_meeting_speakers_meeting_label
    on meeting_speakers (meeting_id, label);
