-- V6 — sync-log counters for the Brevo roster step.
--
-- These two columns were briefly added by editing the already-applied V4, which broke Flyway
-- validation on databases that had V4 applied (checksum mismatch → boot crash). They are moved here
-- into their own migration and made idempotent with `if not exists`, so a database that already has
-- the columns (from the earlier V4 edit) skips them cleanly, while a fresh database gets them here.
-- Default 0 + not null so the columns apply to existing sync_logs rows.

alter table sync_logs add column if not exists roster_students_upserted integer default 0 not null;
alter table sync_logs add column if not exists roster_students_removed  integer default 0 not null;
