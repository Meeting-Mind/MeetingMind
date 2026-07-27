do $$
begin
    if exists (select 1 from pg_roles where rolname = 'meetingmind_core_app') then
        grant select, delete on table chunk_source_segments to meetingmind_core_app;
    end if;
end
$$;

comment on table chunk_source_segments is
    'Transcript-to-chunk provenance links; Core may remove stale links during authoritative STT projection.';
