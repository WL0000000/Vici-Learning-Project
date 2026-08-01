-- V4 — Brevo-sourced student roster (onsite finding 2026-07-30).
--
-- The real student roster lives in Brevo (CONTACT_TYPE=Student), keyed by EXT_ID, with the true
-- Active/Paused/Dropped/Completed status from CONTACT_STATUS. The pre-existing `students` table is
-- really the SimplyBook client/account (bookings/invoices/memberships link to it) and is left as-is.

create table roster_students (
    ext_id     varchar(255) not null,
    name       varchar(255) not null,
    email      varchar(255),
    phone      varchar(255),
    status     varchar(255) default 'ACTIVE' not null
               check (status in ('ACTIVE','PAUSED','DROPPED','COMPLETED')),
    account_id varchar(255),
    synced_at  timestamp(6) not null,
    deleted_at timestamp(6),
    primary key (ext_id)
);

-- The status enum is shared, so widen the existing students CHECK to the four values too (idempotent;
-- students only ever holds ACTIVE/PAUSED in practice, but the constraint must allow the enum's range).
alter table students drop constraint if exists students_status_check;
alter table students add constraint students_status_check
    check (status in ('ACTIVE','PAUSED','DROPPED','COMPLETED'));
