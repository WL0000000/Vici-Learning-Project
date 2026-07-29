-- V3 — Enrich memberships with the fields the real export/API expose (Meeting #3, live data
-- 2026-07-23): unlimited-style packages, the purchase date, the linked invoice number, and the
-- recurring/auto-renew flag. All nullable — absent on older API shapes and (except where the
-- seeder sets them) on seed rows. Backs the family view's "sessions left / expires / next invoice".
ALTER TABLE memberships ADD COLUMN unlimited      boolean;
ALTER TABLE memberships ADD COLUMN recurring      boolean;
ALTER TABLE memberships ADD COLUMN purchase_date  timestamp(6);
ALTER TABLE memberships ADD COLUMN invoice_number varchar(255);
