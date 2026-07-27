do $$
begin
    if exists (select 1 from pg_roles where rolname = 'meetingmind_core_app') then
        revoke insert, update on table chunk_source_segments from meetingmind_core_app;
        grant select, delete on table chunk_source_segments to meetingmind_core_app;
    end if;
end
$$;

comment on table chunk_source_segments is
    'Transcript-to-chunk provenance links; Core may read and remove stale links, while AI owns writes.';
