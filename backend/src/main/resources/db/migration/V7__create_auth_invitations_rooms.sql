create table auth_identities (
    id varchar(64) primary key,
    user_id varchar(64) not null references users(id),
    provider varchar(16) not null,
    provider_user_id varchar(320) not null,
    password_hash varchar(255),
    created_at timestamptz not null default now(),
    last_used_at timestamptz,
    constraint auth_identities_provider_check check (provider in ('local', 'google')),
    constraint auth_identities_provider_user_id_not_blank check (
        length(trim(provider_user_id)) > 0
    ),
    constraint auth_identities_password_check check (
        (provider = 'local' and password_hash is not null and length(trim(password_hash)) > 0)
        or (provider = 'google' and password_hash is null)
    )
);

create unique index ux_auth_identities_provider_user
    on auth_identities (provider, provider_user_id);

create index ix_auth_identities_user
    on auth_identities (user_id);

create table auth_sessions (
    id varchar(64) primary key,
    user_id varchar(64) not null references users(id),
    refresh_token_hash varchar(128) not null unique,
    issued_at timestamptz not null default now(),
    expires_at timestamptz not null,
    revoked_at timestamptz,
    user_agent varchar(512),
    constraint auth_sessions_refresh_token_hash_not_blank check (
        length(trim(refresh_token_hash)) > 0
    ),
    constraint auth_sessions_expiry_check check (expires_at > issued_at),
    constraint auth_sessions_revoked_at_check check (
        revoked_at is null or revoked_at >= issued_at
    )
);

create index ix_auth_sessions_user_state
    on auth_sessions (user_id, revoked_at, expires_at);

create table space_invitations (
    id varchar(64) primary key,
    space_id varchar(64) not null references spaces(id),
    email varchar(320) not null,
    role varchar(32) not null,
    status varchar(32) not null default 'PENDING',
    token_hash varchar(128) not null unique,
    expires_at timestamptz not null,
    accepted_at timestamptz,
    declined_at timestamptz,
    constraint space_invitations_email_not_blank check (length(trim(email)) > 0),
    constraint space_invitations_role_check check (role in ('ADMIN', 'MEMBER')),
    constraint space_invitations_status_check check (
        status in ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED')
    ),
    constraint space_invitations_token_hash_not_blank check (
        length(trim(token_hash)) > 0
    ),
    constraint space_invitations_resolution_check check (
        (status = 'ACCEPTED' and accepted_at is not null and declined_at is null)
        or (status = 'DECLINED' and declined_at is not null and accepted_at is null)
        or (status in ('PENDING', 'EXPIRED') and accepted_at is null and declined_at is null)
    )
);

create unique index ux_space_invitations_pending_email
    on space_invitations (space_id, lower(email))
    where status = 'PENDING';

create index ix_space_invitations_expiry
    on space_invitations (expires_at)
    where status = 'PENDING';

create table meeting_invitations (
    id varchar(64) primary key,
    meeting_id varchar(64) not null references meetings(id),
    email varchar(320) not null,
    meeting_role varchar(32) not null,
    participant_type varchar(16) not null,
    status varchar(32) not null default 'PENDING',
    token_hash varchar(128) not null unique,
    expires_at timestamptz not null,
    accepted_at timestamptz,
    declined_at timestamptz,
    constraint meeting_invitations_email_not_blank check (length(trim(email)) > 0),
    constraint meeting_invitations_role_check check (
        meeting_role in ('HOST', 'EDITOR', 'VIEWER')
    ),
    constraint meeting_invitations_participant_type_check check (
        participant_type in ('member', 'guest')
    ),
    constraint meeting_invitations_status_check check (
        status in ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED')
    ),
    constraint meeting_invitations_token_hash_not_blank check (
        length(trim(token_hash)) > 0
    ),
    constraint meeting_invitations_resolution_check check (
        (status = 'ACCEPTED' and accepted_at is not null and declined_at is null)
        or (status = 'DECLINED' and declined_at is not null and accepted_at is null)
        or (status in ('PENDING', 'EXPIRED') and accepted_at is null and declined_at is null)
    )
);

create unique index ux_meeting_invitations_pending_email
    on meeting_invitations (meeting_id, lower(email))
    where status = 'PENDING';

create index ix_meeting_invitations_expiry
    on meeting_invitations (expires_at)
    where status = 'PENDING';

create table meeting_rooms (
    id varchar(64) primary key,
    meeting_id varchar(64) not null references meetings(id),
    provider varchar(32) not null,
    provider_room_name varchar(255) not null,
    opened_at timestamptz not null default now(),
    closed_at timestamptz,
    constraint meeting_rooms_provider_not_blank check (length(trim(provider)) > 0),
    constraint meeting_rooms_provider_room_name_not_blank check (
        length(trim(provider_room_name)) > 0
    ),
    constraint meeting_rooms_closed_at_check check (
        closed_at is null or closed_at >= opened_at
    )
);

create unique index ux_meeting_rooms_open_meeting
    on meeting_rooms (meeting_id)
    where closed_at is null;

create unique index ux_meeting_rooms_open_provider_room
    on meeting_rooms (provider, provider_room_name)
    where closed_at is null;
