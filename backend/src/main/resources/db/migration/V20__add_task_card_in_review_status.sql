alter table task_cards
    drop constraint task_cards_status_check;

alter table task_cards
    add constraint task_cards_status_check
    check (status in ('TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE'));
