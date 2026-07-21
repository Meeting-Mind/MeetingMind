alter table task_cards
    add column priority varchar(16) not null default 'MEDIUM',
    add column labels text[] not null default '{}';

alter table task_cards
    add constraint task_cards_priority_check check (priority in ('LOW', 'MEDIUM', 'HIGH'));
