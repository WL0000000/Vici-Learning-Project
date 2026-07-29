-- V2 — Capture SimplyBook REST v2's explicit invoice paid flag (payment_received).
--
-- Invoice.isPaid() previously relied only on the free-text `status` string, whose exact values
-- vary per account. The API also returns a `payment_received` flag (and a `payment_datetime`),
-- which is a more reliable paid/unpaid signal; this column stores it. Nullable on purpose: absent
-- on older API shapes and on seeded rows that only set `status`, in which case isPaid() falls back
-- to the status string.
ALTER TABLE invoices ADD COLUMN payment_received boolean;
