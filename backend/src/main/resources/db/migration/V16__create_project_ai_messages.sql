create table project_ai_messages (
    id varchar(96) primary key,
    space_id varchar(64) not null references spaces(id),
    user_id varchar(64) not null references users(id),
    role varchar(16) not null check (role in ('USER', 'ASSISTANT')),
    content text not null,
    created_at timestamptz not null
);

create index ix_project_ai_messages_space_user_created
    on project_ai_messages (space_id, user_id, created_at desc);
