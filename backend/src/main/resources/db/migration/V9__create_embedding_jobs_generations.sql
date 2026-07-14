create table embedding_jobs (
    id varchar(128) primary key,
    space_id varchar(64) not null references spaces(id),
    project_knowledge_id varchar(64) references project_knowledge(id),
    meeting_id varchar(64) references meetings(id),
    status varchar(32) not null default 'PENDING',
    model varchar(128),
    dimension integer,
    generation integer not null,
    attempt_count integer not null default 0,
    failure_code varchar(64),
    created_at timestamptz not null default now(),
    started_at timestamptz,
    completed_at timestamptz,
    constraint embedding_jobs_source_check check (
        project_knowledge_id is not null or meeting_id is not null
    ),
    constraint embedding_jobs_status_check check (
        status in ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')
    ),
    constraint embedding_jobs_model_dimension_check check (
        (model is null and dimension is null)
        or (model is not null and length(trim(model)) > 0 and dimension is not null and dimension > 0)
    ),
    constraint embedding_jobs_generation_check check (generation > 0),
    constraint embedding_jobs_attempt_count_check check (attempt_count >= 0),
    constraint embedding_jobs_lifecycle_check check (
        (status = 'PENDING' and started_at is null and completed_at is null and failure_code is null)
        or (status = 'PROCESSING' and started_at is not null and completed_at is null and failure_code is null)
        or (status = 'COMPLETED' and started_at is not null and completed_at is not null and failure_code is null)
        or (
            status = 'FAILED'
            and started_at is not null
            and completed_at is not null
            and failure_code is not null
            and length(trim(failure_code)) > 0
        )
    ),
    constraint embedding_jobs_completed_at_check check (
        completed_at is null or completed_at >= started_at
    )
);

create index ix_embedding_jobs_space_status
    on embedding_jobs (space_id, status, created_at);

create index ix_embedding_jobs_knowledge_generation
    on embedding_jobs (project_knowledge_id, generation desc)
    where project_knowledge_id is not null;

create index ix_embedding_jobs_meeting_generation
    on embedding_jobs (meeting_id, generation desc)
    where meeting_id is not null;

alter table embedding_chunks
    add column embedding_job_id varchar(128) references embedding_jobs(id),
    add column generation integer not null default 1,
    add column is_active boolean not null default true,
    add column replaced_at timestamptz,
    add constraint embedding_chunks_generation_check check (generation > 0),
    add constraint embedding_chunks_replaced_at_check check (
        is_active = true or replaced_at is not null
    ),
    add constraint embedding_chunks_project_space_check check (project_id = space_id) not valid,
    add constraint embedding_chunks_project_space_fk
        foreign key (project_id) references spaces(id) not valid;

create index ix_embedding_chunks_job
    on embedding_chunks (embedding_job_id)
    where embedding_job_id is not null;

create index ix_embedding_chunks_active_scope_source
    on embedding_chunks (space_id, scope, source_type, source_id, generation desc)
    where is_active = true;
