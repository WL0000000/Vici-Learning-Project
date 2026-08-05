-- V5 — store each roster student's Brevo numeric contact id.
--
-- Sara's model (confirmed from her meeting notes): a family is a Brevo *Company* under Associations,
-- named after the family, whose linkedContactsIds are its student contacts. To bootstrap a roster
-- student's family from that link we need the student's Brevo contact id — the reliable join key the
-- company references (never email). Nullable: unknown for rows synced before this column existed, and
-- for any contact Brevo doesn't return an id for.

alter table roster_students add column brevo_contact_id bigint;
