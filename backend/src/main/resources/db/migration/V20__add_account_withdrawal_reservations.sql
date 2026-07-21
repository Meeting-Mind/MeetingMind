create table account_withdrawal_reservations (
    auth_user_id uuid primary key references auth_user_mappings(auth_user_id),
    core_user_id varchar(64) not null references users(id),
    status varchar(16) not null,
    prepared_at timestamptz not null,
    expires_at timestamptz not null,
    completed_at timestamptz,
    anonymize_at timestamptz,
    anonymized_at timestamptz,
    cancelled_at timestamptz,
    constraint account_withdrawal_reservations_status_check
        check (status in ('PREPARED', 'COMPLETED', 'CANCELLED')),
    constraint account_withdrawal_reservations_prepared_expiry_check
        check (expires_at > prepared_at),
    constraint account_withdrawal_reservations_completion_check
        check ((status = 'COMPLETED') = (completed_at is not null and anonymize_at is not null)),
    constraint account_withdrawal_reservations_cancel_check
        check ((status = 'CANCELLED') = (cancelled_at is not null))
);

create index ix_account_withdrawal_reservations_anonymize
    on account_withdrawal_reservations (anonymize_at)
    where status = 'COMPLETED' and anonymized_at is null;

comment on table account_withdrawal_reservations is
    'Core-owned withdrawal saga state. PREPARED is never sufficient to anonymize a user.';
