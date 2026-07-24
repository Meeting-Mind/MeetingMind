alter table meeting_invitations
    alter column email drop not null;

alter table meeting_invitations
    drop constraint if exists meeting_invitations_email_not_blank;
