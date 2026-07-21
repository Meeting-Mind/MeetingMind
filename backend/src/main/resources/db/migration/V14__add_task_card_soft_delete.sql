alter table task_cards
    add column deleted_at timestamptz;

create index ix_task_cards_active_space_status
    on task_cards (space_id, status)
    where deleted_at is null;
