create table meeting_ai_messages (
    id varchar(96) primary key,
    meeting_id varchar(64) not null references meetings(id),
    user_id varchar(64) not null references users(id),
    role varchar(16) not null check (role in ('USER', 'ASSISTANT')),
    content text not null,
    created_at timestamptz not null
);

create index ix_meeting_ai_messages_meeting_user_created
    on meeting_ai_messages (meeting_id, user_id, created_at desc);

do $$
begin
    if exists (select 1 from pg_roles where rolname = 'meetingmind_core_app') then
        grant select, insert on table meeting_ai_messages to meetingmind_core_app;
    end if;
end
$$;

comment on table meeting_ai_messages is
    'Meeting-scoped AI conversation history; rows are untrusted query context and never RAG evidence.';
