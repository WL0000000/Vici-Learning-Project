# Onsite Integration Checklist — Vici Learning

Working doc for the onsite visit with Sarah. Goal: connect the dashboard to Vici's **real**
SimplyBook.me and Brevo accounts, and resolve the few data-shape unknowns the sandbox couldn't
answer. Everything under "What we know works" was validated against live sandbox APIs.

**Roadmap**
- **This week:** on-site testing, confirm her data field names match ours so there are no errors,
  demo, questions, maybe small installs on her computer.
- **Next week:** create the working database on-site, connect real client data securely, show results.
- **The week after:** final hand-off on-site. Confirm what she wants going forward, who stays in the
  loop, and leave me as the point of contact for any issues.


---

## 🆕 UPDATED 2026-07-29 — what changed since the field validation

- **Schema is now Flyway-managed** (migrations V1–V3), `ddl-auto=validate`. On a **fresh** Vici Neon,
  Flyway builds the whole schema itself — including the ADMIN/STAFF/TUTOR role CHECK constraint, so
  the old manual `app_users_role_check` ALTER is **no longer needed**. Verified: V1–V3 apply cleanly
  on a fresh DB and on an existing-schema baseline.
  - ⚠️ **On Render, make sure `SPRING_JPA_HIBERNATE_DDL_AUTO` is `validate` or unset** (our default is
    validate). If a leftover `=update` env var is still on Render it will fight Flyway — remove it.
- **Sync is per-step failure-isolated.** If memberships/invoices hit an odd shape, students, bookings
  and associations still sync — one bad step won't blank the dashboard. The run is marked failed but
  partial data lands, and the failing step + reason show in the sync log.
- **Tutor portal now shows REAL synced data** (not sample data) — the old caveat below is resolved.
- **Invoice paid/unpaid** now prefers the `payment_received` flag; **memberships** capture
  unlimited / purchase date / invoice number and parse defensively. Both re-verify on the sync.
- **Category & Location** are read off the **booking** (`event_category`/`location`) — no longer a
  blocker (still eyeball that the Families columns populate).

### Pre-flight: run the read-only harness (rebuilt, `onsite-harness.py`)
With her keys in `.env`:  `python onsite-harness.py`
It's read-only + value-masked. It confirms the exact names our matching needs — Brevo `EXT_ID`,
`STUDENT_STATUS`, `VICI_ACCOUNT_ID`; SimplyBook `Account_ID` field; membership/invoice keys. Any
**✗ MISSING** = fix that `@JsonProperty`/field title to her real name **before** Sync Now.

### After Sync Now: the "did it line up?" signals (this is the whole ballgame)
On `/sync`, the last-run counters. Green run + all these > 0 means the joins matched:
- `accountIdsLinked` — Account_ID pulled from SimplyBook
- `extIdsLinked` — EXT_ID stamped from Brevo **by email** → **if 0, the email match or the EXT_ID
  attribute name is off** (the parent-vs-student email edge — the thing most likely to bite)
- `familyLinksLinked` — families from Brevo Companies · `statusesUpdated` — ACTIVE/PAUSED from Brevo
- **Email is the universal join key** (Brevo contact email ↔ local student email ↔ SimplyBook client
  email). If counts are 0 but the attributes exist, it's an email mismatch — walk a real family
  through it with Sara.

---

## ✅ What we KNOW works (validated against real APIs)

- **SimplyBook auth** — works via an **API User Key** (bypasses the new-IP block + 2FA that broke
  password auth from the cloud). Both JSON-RPC and REST v2.
- **SimplyBook clients** — `id / name / email / phone` map correctly.
- **SimplyBook tutors** — `id / name / email / phone / is_visible` map correctly.
- **SimplyBook bookings** — fixed to the real shape: datetime in `start_date`/`end_date`, confirm
  flag is `is_confirm`.
- **SimplyBook Account_ID custom field** — the `field-values` shape is confirmed; the code reads it
  correctly.
- **Brevo Company → contact link** — `GET /companies` → `linkedContactsIds` works; that's the
  family → students bridge.
- **Brevo EXT_ID** — reads the **EXT_ID attribute** (Brevo never returns the top-level `ext_id`).

---

## 🔧 DO ONSITE — setup actions

- [ ] **Generate an API User Key on Vici's SimplyBook.** Custom Features → API → click the
      **"API User Keys"** link in the blue note text (it's a link, not a menu).
- [ ] **Set the SimplyBook values in Render:** `SIMPLYBOOK_COMPANY_LOGIN`, `SIMPLYBOOK_ADMIN_USERNAME`,
      `SIMPLYBOOK_API_USER_KEY`. Leave `SIMPLYBOOK_ADMIN_PASSWORD` blank (the key replaces it).
- [ ] **Set the Brevo key in Render:** `BREVO_API_KEY`.
- [ ] **Set the Notion values in Render:** `NOTION_TOKEN`, `NOTION_TUTORS_DATA_SOURCE_ID`.
- [ ] **Also put her keys in the local `.env`** on the laptop, so we can run the read-only harness.
- [ ] **Run a "Sync Now"** from `/sync` and watch the counts + log for per-step errors.
- [ ] **Turn OFF the `seed` profile** in Render (`SPRING_PROFILES_ACTIVE` without `seed`) after the
      first clean sync, so mock data doesn't mix with real data.
- [ ] **Clear her keys from the local `.env`** when done. Rotate anything pasted insecurely.

---

## 🔎 We confirm this OURSELVES with the API key (no need to ask her)

Once her key is in, the read-only harness reads her actual forms and fields. If any name differs
from our defaults, we fix the code on the spot. So we do **not** need to ask her about:

- [ ] **Brevo attribute names** — EXT_ID, family/Account_ID, and status. We read her contacts and
      see the exact names, then match the code to them.
- [ ] **SimplyBook Account_ID custom field** — whether it exists and its exact title. We read her
      client fields directly.
- [ ] **Whether students have their own email or share the parent's** — we see this in her Brevo
      contacts and SimplyBook clients.
- [ ] **Membership / invoice field names** — we read them from her real records.
- [ ] **Booking field shapes** — already fixed and confirmed; we re-verify against her data.

---

## ❓ ASK SARAH — the few things the API can't tell us

- [ ] **Service Category & Location.** These are **not** in the SimplyBook API, so we can't read
      them. Where do you track them (service custom fields, a naming convention, only in the export)?
      The Families view columns depend on the answer, otherwise they stay blank.
- [ ] **Booking approval & cancellations.** Do you use booking **approval**, or are bookings
      auto-confirmed? And when a booking is **cancelled**, does it stay in the system or disappear?
      (This is a settings/business answer only you have, and it decides how we count cancellations.)
- [ ] **Optional write features.** Do you want **email reminders** (needs Brevo write access) and
      **dashboard-side tutor editing** (needs Notion write access)? If not, read-only keys are enough.

---

## Reassuring her about data (say this if she's worried)

> Setting this up is completely read-only. We're not changing anything in your SimplyBook or Brevo.
> You generate the keys yourself, they stay in your account, and you can revoke them any time. We
> never see your passwords. When we check that everything lines up, our tool reads only the
> **structure** of your data, the field names and layout, not the actual records. Your clients'
> names, emails, and bookings are blanked out. Nothing of yours goes into our code or GitHub, and
> once it's running your data sits in a database under **your own account** that you control and can
> delete anytime.

Strongest single point: **your booking system is never modified. We only read from it.**

---

## ⚠️ Flag / know before the visit

- ~~Tutor portal shows sample data~~ — **RESOLVED:** it now renders real synced data.
- **Notion editing needs a write-enabled token** (the integration must have "can edit" on the tutor
  database), otherwise saving a tutor fails.
- **Status toggle write-back needs a Brevo WRITE-scoped key.** If her `BREVO_API_KEY` is read-only,
  the ACTIVE/PAUSED toggle still works locally but won't push to Brevo (it degrades gracefully, no
  crash). The sync itself only READS Brevo, so read-only is fine for the sync.
- **Memberships & invoices** field names now parse defensively and were checked against real shapes —
  still eyeball them on the first live sync (the harness confirms the names up front).

---

## Post-onsite (Render / prod)

- [ ] First real **Sync Now** succeeds end to end (all steps green).
- [ ] Dashboard spot-check: Overview, Students, Associations, Tutors render with real data.
- [ ] EXT_ID + Account_ID populate on the Students / Associations pages.
- [ ] Her data lives in a **Vici-owned Neon** database; delete the team's sandbox Neon.
- [ ] Merge the open fix branches.
