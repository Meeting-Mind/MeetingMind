alter table meetings add column if not exists room_code text;

create index if not exists idx_meetings_space_room_code on meetings (space_id, room_code)
where deleted_at is null and room_code is not null;
