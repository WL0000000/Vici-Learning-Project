-- V9 — add a fifth enrolment status BLANK.
--
-- Students whose Brevo CONTACT_STATUS is empty or unrecognized used to default to ACTIVE, which
-- inflated the "Active Students" card vs Sara's "Active Students" segment. They now map to their own
-- BLANK bucket (filterable on /students), so Active counts only real actives. Widen the CHECK
-- constraint on both status columns to allow the new value. Idempotent (drop-if-exists then add).

alter table roster_students drop constraint if exists roster_students_status_check;
alter table roster_students add constraint roster_students_status_check
    check (status in ('ACTIVE','PAUSED','DROPPED','COMPLETED','BLANK'));

-- The status enum is shared with the (SimplyBook) students table, whose CHECK was set in V4.
alter table students drop constraint if exists students_status_check;
alter table students add constraint students_status_check
    check (status in ('ACTIVE','PAUSED','DROPPED','COMPLETED','BLANK'));
