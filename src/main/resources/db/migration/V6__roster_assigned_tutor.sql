-- V6 — store each roster student's primary assigned tutor, from Brevo's ASSIGNED_TUTOR field.
--
-- Sara flagged (onsite, Meeting 5 follow-up) that deriving "my students" from SimplyBook.me
-- booking history is unreliable: a tutor who only substituted a single session for someone
-- else's regular student would incorrectly show up as that student's assigned tutor. Brevo's
-- ASSIGNED_TUTOR field on the student contact is the actual primary-assignment source of truth,
-- distinct from who happened to teach any one session.

alter table roster_students add column assigned_tutor varchar(255);