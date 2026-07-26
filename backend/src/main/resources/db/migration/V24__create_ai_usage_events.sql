create table if not exists ai_usage_events (
    id varchar(80) primary key,
    space_id varchar(80) not null references spaces(id),
    meeting_id varchar(80),
    feature varchar(32) not null,
    provider varchar(64),
    api_style varchar(64),
    streamed boolean not null default false,
    input_tokens integer,
    output_tokens integer,
    total_tokens integer,
    total_ms bigint,
    created_at timestamptz not null
);

create index if not exists idx_ai_usage_events_space_created_at
    on ai_usage_events(space_id, created_at desc);

create index if not exists idx_ai_usage_events_meeting_created_at
    on ai_usage_events(meeting_id, created_at desc);
