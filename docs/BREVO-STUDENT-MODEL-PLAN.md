# Plan — Brevo-sourced student roster

> **STATUS (2026-08-01): Phase 1 SHIPPED (PR #99, merged).** Built additively as a new `RosterStudent`
> entity (not the `BookingAccount` option below — `Student` was left as the SimplyBook account, roster
> is separate). Fixes roster count / EXT_ID / status. **Phase 2 (per-student hours) is blocked** on a
> booking→student link (Sara: none available). See the memory `brevo-roster-phase1` for the live shape.


Fixes the three linked onsite defects at their root: roster shows 250 (should be ~190), EXT_ID blank
(0/250), and Active/Paused wrong. All three stem from one design mistake — we build the student list
from **SimplyBook clients** (parents/accounts, with dupes and dead records) and try to match Brevo by
**email** (unreliable — some students have none). This realigns us with the documented Meeting #4
model: *identity (name, EXT_ID, family, status) comes from Brevo; sessions/hours come from SimplyBook.*

## What we now know (onsite, 2026-07-30)
- **No parent-child in SimplyBook** — each client is a standalone record; ~250 records, ~190 real students.
- **Brevo is the student system of record.** Per student: `EXT_ID` (unique key), `CONTACT_TYPE` (list —
  classifies the contact), `CONTACT_STATUS` (list — real Active/Paused), `STUDENT_NAME`, `ACCOUNT_CODE`
  (family), email/phone (optional, often missing).
- `STUDENT_STATUS` (what we read today) is **not** the right status field — it's `CONTACT_STATUS`.
- Email can never be the join key.

## Target model
| Concern | Source | Key |
|---|---|---|
| Student identity, status, family | **Brevo contact** (CONTACT_TYPE = student) | **EXT_ID** |
| Bookings / hours / sessions | SimplyBook | SimplyBook client id |
| Family / association | Account_ID (SimplyBook) ↔ ACCOUNT_CODE (Brevo) | family key |

## Phase 1 — Brevo students as the roster (fixes roster count, EXT_ID, status)
Independently shippable; fixes 3 of the 4 issues without touching bookings.
1. Extend the Brevo contact model to capture `CONTACT_TYPE`, `CONTACT_STATUS`, `STUDENT_NAME`,
   `ACCOUNT_CODE` (list-typed attributes → parse the first element).
2. New sync step `syncBrevoStudents`: page all contacts, keep those whose `CONTACT_TYPE` marks a
   **current student**, upsert a `Student` keyed by **EXT_ID**, setting name, status (from
   `CONTACT_STATUS`), family (`ACCOUNT_CODE`), email/phone.
3. Make the `CONTACT_TYPE` "student" value and the `CONTACT_STATUS` active/paused values **configurable**
   (`brevo.student-contact-type`, `brevo.status-active`, `brevo.status-paused`) so we can set them the
   moment we re-verify against her account, without a code change.
4. Retire the email-based EXT_ID/STUDENT_STATUS matching steps (superseded — identity now comes from
   the source record).

**Schema decision (the crux of Phase 1):** the current `Student` is keyed by SimplyBook client id
(`Long`) and `Booking` FKs to it. Two options:
- **A. Rekey `Student` on EXT_ID** and re-map booking attribution via family (below). Cleanest end-state,
  bigger refactor (Booking FK, associations, metrics, tests).
- **B. Add a `BookingAccount` entity** for the SimplyBook client (bookings hang off it) and make
  `Student` the Brevo-sourced roster; link the two by family. Less rekeying, more entities.

Recommendation: **B** — it matches reality (a SimplyBook "client" is a booking account, not a student)
and avoids a risky primary-key migration, at the cost of one new table.

## Phase 2 — attribute SimplyBook bookings to individual students
This is gated by ONE unknown: **how does a SimplyBook booking identify the student?**
- **If the booking (or its client) carries the student** (an EXT_ID custom field, or a per-student
  Account_ID) → join directly → **exact per-student hours/sessions**.
- **If bookings only carry the family** (`Account_ID` ↔ `ACCOUNT_CODE`) → attribute to the family; hours
  are **per-family**, split to students only when the family has exactly one student.

Until this is answered, Phase 1 ships and per-student hours fall back to family-level.

## Phase 3 — reconcile Families/associations with the new model
The Association page already owns the family↔student map; point it at the Brevo-sourced students and the
`ACCOUNT_CODE` family key. Mostly wiring once Phase 1 lands.

## Scope / risk
- Phase 1 is a real change (new sync step, Brevo model fields, schema — a Flyway V4). Medium.
- Blocked verifications (API keys revoked): exact `CONTACT_TYPE` student value; `CONTACT_STATUS`
  active/paused strings; the booking→student join. All buildable behind config, but need one more
  read-only API window (or Sara's answers) to switch on with confidence.

## Rollout
Build Phase 1 behind config on a branch → re-verify the two attribute values in a read-only API window
→ set config → Sync Now → confirm ~190 students, EXT_IDs populated, correct Active/Paused. Then Phase 2
once the booking-join is known.
