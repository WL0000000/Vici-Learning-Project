-- V7 — replace the binary active/inactive distinction with a 3-way tutor status
-- (Active / Inactive / Legacy), per client feedback: a tutor who is no longer active
-- should be marked Legacy rather than deleted, so their record and history stay intact.

alter table tutors add column status varchar(20) not null default 'INACTIVE';

-- Tutors previously soft-deleted (no longer found in SimplyBook.me) become Legacy instead of
-- staying soft-deleted, matching the new "don't delete, distinguish" behavior.
update tutors set status = 'LEGACY' where deleted_at is not null;
update tutors set deleted_at = null where deleted_at is not null;

-- First-pass backfill from the old active flag for tutors still present. This is only a
-- starting point — the next sync recomputes Active/Inactive for every non-Legacy tutor from
-- real RosterStudent assignments.
update tutors set status = 'ACTIVE' where active = true;
