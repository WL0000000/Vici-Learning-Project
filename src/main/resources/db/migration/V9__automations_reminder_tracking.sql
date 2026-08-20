-- V9: track when an automated renewal/payment reminder was last sent, so revisiting the
-- Automations page doesn't re-send the same reminder every time. Payment reminders are tiered
-- (2 weeks / 72 hours / 12 hours before a session), and re-sending is allowed once urgency
-- escalates to a new tier, so last_reminder_tier records which tier went out last, not just
-- whether one did.

alter table invoices add column last_reminder_tier varchar(20);
alter table invoices add column last_reminder_sent_at timestamp(6);

alter table memberships add column last_renewal_reminder_sent_at timestamp(6);
