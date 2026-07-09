create extension if not exists vector;

create table project_knowledge (
    id varchar(64) primary key,
    space_id varchar(64) not null references spaces(id),
    type varchar(32) not null,
    title varchar(200) not null,
    content text not null,
    source_meeting_id varchar(64) references meetings(id),
    approved_by varchar(64) references users(id),
    status varchar(32) not null default 'PUBLISHED',
    embedding_status varchar(32) not null default 'PENDING',
    embedding_job_id varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint project_knowledge_type_check check (type in ('report', 'decision', 'manual', 'external')),
    constraint project_knowledge_status_check check (status in ('PUBLISHED', 'ARCHIVED')),
    constraint project_knowledge_embedding_status_check check (
        embedding_status in ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')
    ),
    constraint project_knowledge_title_not_blank check (length(trim(title)) > 0),
    constraint project_knowledge_content_not_blank check (length(trim(content)) > 0)
);

create index ix_project_knowledge_space_type_updated_at
    on project_knowledge (space_id, type, updated_at);

create index ix_project_knowledge_space_status
    on project_knowledge (space_id, status);

create index ix_project_knowledge_source_meeting
    on project_knowledge (source_meeting_id)
    where source_meeting_id is not null;

create index ix_project_knowledge_embedding_status
    on project_knowledge (space_id, embedding_status);

create table embedding_chunks (
    id varchar(64) primary key,
    space_id varchar(64) not null references spaces(id),
    project_id varchar(64) not null,
    meeting_id varchar(64) references meetings(id),
    project_knowledge_id varchar(64) references project_knowledge(id),
    scope varchar(16) not null,
    source_type varchar(32) not null,
    source_id varchar(64) not null,
    title varchar(200) not null,
    speaker_names jsonb not null default '[]'::jsonb,
    start_ms integer,
    end_ms integer,
    content text not null,
    embedding_text text not null,
    metadata jsonb not null default '{}'::jsonb,
    embedding vector,
    created_at timestamptz not null default now(),
    constraint embedding_chunks_project_id_not_blank check (length(trim(project_id)) > 0),
    constraint embedding_chunks_scope_check check (scope in ('meeting', 'project')),
    constraint embedding_chunks_source_type_check check (
        source_type in (
            'transcript',
            'meetingSummary',
            'decision',
            'actionItem',
            'report',
            'projectKnowledge',
            'glossary'
        )
    ),
    constraint embedding_chunks_source_id_not_blank check (length(trim(source_id)) > 0),
    constraint embedding_chunks_title_not_blank check (length(trim(title)) > 0),
    constraint embedding_chunks_content_not_blank check (length(trim(content)) > 0),
    constraint embedding_chunks_embedding_text_not_blank check (length(trim(embedding_text)) > 0),
    constraint embedding_chunks_meeting_scope_check check (scope <> 'meeting' or meeting_id is not null),
    constraint embedding_chunks_project_knowledge_source_check check (
        source_type <> 'projectKnowledge' or project_knowledge_id is not null
    ),
    constraint embedding_chunks_time_check check (
        (start_ms is null and end_ms is null)
        or (start_ms is not null and end_ms is not null and start_ms >= 0 and end_ms >= start_ms)
    ),
    constraint embedding_chunks_speaker_names_array check (jsonb_typeof(speaker_names) = 'array'),
    constraint embedding_chunks_metadata_object check (jsonb_typeof(metadata) = 'object')
);

create index ix_embedding_chunks_scope_source
    on embedding_chunks (space_id, scope, source_type, source_id);

create index ix_embedding_chunks_meeting_scope
    on embedding_chunks (space_id, meeting_id, scope)
    where meeting_id is not null;

create index ix_embedding_chunks_project_knowledge
    on embedding_chunks (space_id, project_knowledge_id)
    where project_knowledge_id is not null;

create table chunk_source_segments (
    id varchar(64) primary key,
    chunk_id varchar(64) not null references embedding_chunks(id),
    segment_id varchar(64) not null references transcript_segments(id),
    segment_order integer not null default 0,
    constraint chunk_source_segments_order_check check (segment_order >= 0)
);

create unique index ux_chunk_source_segments_chunk_segment
    on chunk_source_segments (chunk_id, segment_id);

create index ix_chunk_source_segments_segment
    on chunk_source_segments (segment_id);
