create extension if not exists pg_trgm;

do $$
begin
    if exists (
        select 1
        from embedding_chunks
        where embedding is not null
          and vector_dims(embedding) <> 1536
    ) then
        raise exception 'V11 requires every existing embedding vector to have 1536 dimensions';
    end if;
end
$$;

alter table embedding_chunks
    alter column embedding type vector(1536)
    using embedding::vector(1536);

alter table embedding_jobs
    drop constraint embedding_jobs_source_check,
    add column trigger_reason varchar(64) not null default 'FULL_REINDEX',
    add column content_hash varchar(64),
    add column next_attempt_at timestamptz not null default now(),
    add column lease_expires_at timestamptz,
    add constraint embedding_jobs_source_check check (
        num_nonnulls(project_knowledge_id, meeting_id) = 1
    ),
    add constraint embedding_jobs_trigger_reason_check check (
        trigger_reason in (
            'KNOWLEDGE_CHANGED',
            'TRANSCRIPT_COMPLETED',
            'SPEAKER_UPDATED',
            'REPORT_CONFIRMED',
            'FULL_REINDEX'
        )
    ),
    add constraint embedding_jobs_content_hash_check check (
        content_hash is null or content_hash ~ '^[0-9a-f]{64}$'
    ),
    add constraint embedding_jobs_retry_time_check check (
        next_attempt_at >= created_at
    ),
    add constraint embedding_jobs_lease_check check (
        (status = 'PROCESSING' and lease_expires_at is not null)
        or (status <> 'PROCESSING' and lease_expires_at is null)
    ) not valid;

update embedding_jobs
set lease_expires_at = coalesce(started_at, created_at)
where status = 'PROCESSING' and lease_expires_at is null;

alter table embedding_jobs
    validate constraint embedding_jobs_lease_check;

create unique index ux_embedding_jobs_knowledge_generation
    on embedding_jobs (project_knowledge_id, generation)
    where project_knowledge_id is not null;

create unique index ux_embedding_jobs_meeting_generation
    on embedding_jobs (meeting_id, generation)
    where meeting_id is not null;

create index ix_embedding_jobs_claimable
    on embedding_jobs (next_attempt_at, created_at)
    where status = 'PENDING';

create index ix_embedding_jobs_expired_lease
    on embedding_jobs (lease_expires_at)
    where status = 'PROCESSING';

create index ix_embedding_chunks_embedding_text_trgm
    on embedding_chunks using gin (embedding_text gin_trgm_ops)
    where is_active = true;

create function enqueue_embedding_job(
    target_space_id varchar,
    target_project_knowledge_id varchar,
    target_meeting_id varchar,
    reason varchar
) returns varchar
language plpgsql
as $$
declare
    next_generation integer;
    job_id varchar(128);
    source_lock_key text;
begin
    if num_nonnulls(target_project_knowledge_id, target_meeting_id) <> 1 then
        raise exception 'embedding job source must reference exactly one source';
    end if;

    source_lock_key := coalesce(target_project_knowledge_id, target_meeting_id);
    perform pg_advisory_xact_lock(hashtextextended(source_lock_key, 0));

    if target_project_knowledge_id is not null then
        select coalesce(max(generation), 0) + 1
        into next_generation
        from embedding_jobs
        where project_knowledge_id = target_project_knowledge_id;
    else
        select coalesce(max(generation), 0) + 1
        into next_generation
        from embedding_jobs
        where meeting_id = target_meeting_id;
    end if;

    job_id := 'embedding-job-' || replace(gen_random_uuid()::text, '-', '');
    insert into embedding_jobs (
        id, space_id, project_knowledge_id, meeting_id, generation, trigger_reason
    ) values (
        job_id, target_space_id, target_project_knowledge_id, target_meeting_id,
        next_generation, reason
    );
    return job_id;
end
$$;

create function trigger_project_knowledge_embedding_job() returns trigger
language plpgsql
as $$
declare
    job_id varchar(128);
    source_became_indexable boolean;
    source_changed boolean;
begin
    source_became_indexable := new.status = 'PUBLISHED'
        and new.deleted_at is null
        and (
            tg_op = 'INSERT'
            or old.status <> 'PUBLISHED'
            or old.deleted_at is not null
        );
    source_changed := tg_op = 'UPDATE'
        and new.status = 'PUBLISHED'
        and new.deleted_at is null
        and (
            new.title is distinct from old.title
            or new.content is distinct from old.content
        );

    if source_became_indexable or source_changed then
        job_id := enqueue_embedding_job(new.space_id, new.id, null, 'KNOWLEDGE_CHANGED');
        update project_knowledge
        set embedding_status = 'PENDING', embedding_job_id = job_id
        where id = new.id;
    elsif tg_op = 'UPDATE'
        and old.status = 'PUBLISHED'
        and old.deleted_at is null
        and (new.status <> 'PUBLISHED' or new.deleted_at is not null) then
        update embedding_chunks
        set is_active = false, replaced_at = now()
        where project_knowledge_id = new.id and is_active = true;
    end if;
    return new;
end
$$;

create trigger project_knowledge_embedding_job_trigger
after insert or update on project_knowledge
for each row execute function trigger_project_knowledge_embedding_job();

create function trigger_meeting_transcript_embedding_job() returns trigger
language plpgsql
as $$
declare
    meeting_space_id varchar(64);
begin
    if new.purged_at is not null
        and (tg_op = 'INSERT' or old.purged_at is null) then
        delete from chunk_source_segments
        where chunk_id in (
            select id from embedding_chunks
            where meeting_id = new.meeting_id and source_type = 'transcript'
        );
        update embedding_chunks
        set is_active = false, replaced_at = now()
        where meeting_id = new.meeting_id
          and source_type = 'transcript'
          and is_active = true;
    end if;

    if new.status = 'COMPLETED'
        and new.purged_at is null
        and (tg_op = 'INSERT' or old.status <> 'COMPLETED') then
        select space_id into meeting_space_id from meetings where id = new.meeting_id;
        perform enqueue_embedding_job(
            meeting_space_id, null, new.meeting_id, 'TRANSCRIPT_COMPLETED'
        );
    end if;
    return new;
end
$$;

create trigger meeting_transcript_embedding_job_trigger
after insert or update on meeting_transcripts
for each row execute function trigger_meeting_transcript_embedding_job();

create function trigger_meeting_metadata_embedding_job() returns trigger
language plpgsql
as $$
begin
    if new.title is distinct from old.title
        and exists (select 1 from embedding_jobs where meeting_id = new.id) then
        perform enqueue_embedding_job(new.space_id, null, new.id, 'FULL_REINDEX');
    end if;
    return new;
end
$$;

create trigger meeting_metadata_embedding_job_trigger
after update on meetings
for each row execute function trigger_meeting_metadata_embedding_job();

create function trigger_meeting_speaker_embedding_job() returns trigger
language plpgsql
as $$
declare
    meeting_space_id varchar(64);
begin
    if (new.label, new.display_name) is distinct from (old.label, old.display_name)
        and exists (select 1 from embedding_jobs where meeting_id = new.meeting_id) then
        select space_id into meeting_space_id from meetings where id = new.meeting_id;
        perform enqueue_embedding_job(
            meeting_space_id, null, new.meeting_id, 'SPEAKER_UPDATED'
        );
    end if;
    return new;
end
$$;

create trigger meeting_speaker_embedding_job_trigger
after update on meeting_speakers
for each row execute function trigger_meeting_speaker_embedding_job();

create function trigger_current_report_embedding_job() returns trigger
language plpgsql
as $$
declare
    meeting_space_id varchar(64);
begin
    if new.status = 'CONFIRMED' and new.is_current = true then
        select space_id into meeting_space_id from meetings where id = new.meeting_id;
        perform enqueue_embedding_job(
            meeting_space_id, null, new.meeting_id, 'REPORT_CONFIRMED'
        );
    end if;
    return new;
end
$$;

create trigger current_report_embedding_job_trigger
after insert or update on meeting_reports
for each row execute function trigger_current_report_embedding_job();
