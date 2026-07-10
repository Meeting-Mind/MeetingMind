create table users (
    id varchar(64) primary key,
    email varchar(320) not null,
    display_name varchar(100) not null,
    picture_url text,
    status varchar(32) not null default 'active',
    created_at timestamptz not null default now(),
    last_login_at timestamptz,
    constraint users_email_not_blank check (length(trim(email)) > 0),
    constraint users_display_name_not_blank check (length(trim(display_name)) > 0),
    constraint users_status_check check (status in ('active', 'disabled'))
);

create unique index ux_users_email_lower on users (lower(email));
create index ix_users_status on users (status);

create table spaces (
    id varchar(64) primary key,
    name varchar(120) not null,
    description text,
    created_by varchar(64) not null references users(id),
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    constraint spaces_name_not_blank check (length(trim(name)) > 0)
);

create index ix_spaces_created_by on spaces (created_by);
create index ix_spaces_active_created_at on spaces (created_at) where deleted_at is null;

create table space_members (
    id varchar(64) primary key,
    space_id varchar(64) not null references spaces(id),
    user_id varchar(64) not null references users(id),
    role varchar(32) not null,
    joined_at timestamptz not null default now(),
    removed_at timestamptz,
    constraint space_members_role_check check (role in ('OWNER', 'ADMIN', 'MEMBER'))
);

create unique index ux_space_members_active_user
    on space_members (space_id, user_id)
    where removed_at is null;

create unique index ux_space_members_active_owner
    on space_members (space_id)
    where removed_at is null and role = 'OWNER';

create index ix_space_members_user_active
    on space_members (user_id)
    where removed_at is null;
