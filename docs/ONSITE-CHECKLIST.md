# Onsite Integration Checklist — Vici Learning

Working doc for the onsite visit with Sarah. Goal: connect the dashboard to Vici's **real**
SimplyBook.me and Brevo accounts, and resolve the handful of data-shape unknowns the sandbox
couldn't answer. Everything below was validated against live sandbox APIs (2026-07-20).

---

## How to approach Sarah (5 min)

1. **Lead with the win.** The Association Account + family/balance view she called "golden" is built
   and working. Today is about pointing it at her real data.
2. **Frame it as "3 credentials + a few clarifications."** It's not a rebuild — we need a couple of
   API keys and answers to a few "how do you store X" questions. Keep it concrete.
3. **Respect her time** (she's busy; use the availability windows). Work the "ASK SARAH" list fast,
   then do the "DO ONSITE" setup while she's around in case a key doesn't work.
4. **Don't fully automate anything customer-facing** without her OK (her standing rule).

---

## ✅ What we KNOW works (validated against real APIs)

- **SimplyBook auth** — works via an **API User Key** (bypasses the new-IP block + 2FA that broke
  password auth from the cloud). Both JSON-RPC and REST v2.
- **SimplyBook clients** — `id / name / email / phone` map correctly.
- **SimplyBook tutors** — `id / name / email / phone / is_visible` map correctly.
- **SimplyBook bookings** — fixed to the real shape: datetime in `start_date`/`end_date`, confirm
  flag is `is_confirm`. (Was: null start times + every booking marked "confirmed".)
- **SimplyBook Account_ID custom field** — the `field-values` shape (`fields[].field.title` +
  `value`) is confirmed; `extractAccountId` reads it correctly.
- **Brevo Company → contact link** — `GET /companies` → `linkedContactsIds` works; that's the
  family → students bridge.
- **Brevo EXT_ID** — fixed to read the **EXT_ID attribute** (Brevo never returns the top-level
  `ext_id`). Brevo attributes (Account_ID, status) are readable.

---

## 🔧 DO ONSITE — setup actions

- [ ] **Generate an API User Key on Vici's SimplyBook account.** Custom Features → API → click the
      **"API User Keys"** link in the blue note (it's a link inside that text, not a menu).
- [ ] **Set it in Render:** env var `SIMPLYBOOK_API_USER_KEY` = the key. *Without this, zero
      SimplyBook data flows in prod.*
- [ ] **Set Vici's SimplyBook company login / admin user** in Render: `SIMPLYBOOK_COMPANY_LOGIN`,
      `SIMPLYBOOK_ADMIN_USERNAME`, `SIMPLYBOOK_ADMIN_PASSWORD`, and the REST key
      `SIMPLYBOOK_API_USER_KEY` (same value).
- [ ] **Set Vici's Brevo API key** in Render: `BREVO_API_KEY` (a read-only key is fine).
- [ ] **Turn OFF the `seed` profile in prod** (`SPRING_PROFILES_ACTIVE` without `seed`) so mock data
      doesn't mix with real data. Do this AFTER a first successful sync is confirmed.
- [ ] **Run a "Sync Now"** from the `/sync` page and watch the counts + the log for per-step errors.
- [ ] **Rotate any credentials** that got pasted anywhere insecure.

---

## ❓ ASK SARAH — the unknowns only her data answers

**Brevo attribute names (critical for the association feature):**
- [ ] What is the **EXT_ID** attribute called on a Brevo contact? (we assumed `EXT_ID`)
- [ ] What is the **family / Account_ID** stored as — a contact attribute (we assumed
      `VICI_ACCOUNT_ID`), and/or the **Company name**? What format (e.g. `Gray_Account` vs `Gray`)?
- [ ] What is the **student status** attribute called? (we assumed `STUDENT_STATUS`; values
      Active/Paused?)
- [ ] **Best:** can she give a **read-only Brevo API key** or a **CSV export of one Company + 2
      contacts**? Then we confirm all names in 30 seconds instead of guessing.

**SimplyBook data shapes:**
- [ ] Where is a service's **Category** (Private 1:1 / Study Club / Assessment) and **Location**
      (At Home / Virtual / Centre) stored? They are **NOT** in the SimplyBook API — only in the
      export. Service custom fields? A naming convention on the service name? (Family view columns
      depend on this.)
- [ ] Do they use **booking approval**, or are bookings auto-confirmed? And when a booking is
      **cancelled**, does it stay in the system or disappear? (Determines how we count cancellations —
      the API alone can't tell cancelled from pending.)
- [ ] Confirm the **Account_ID custom field** exists on SimplyBook clients and its exact **title**
      (we default to `Account_ID`).

**Identity model (walk through 1–2 real families):**
- [ ] In SimplyBook the "client" is the **parent**; in Brevo each **student** is a contact. We join
      them **by email**. Does each student have their **own** email in Brevo, or do siblings share
      the parent's email? (If shared, the student↔booking match needs a different key.)

---

## ⚠️ NEEDS WORK / not yet validated (flag, don't promise)

- **Memberships & invoices (REST v2):** endpoints authenticate and paginate correctly, but the
  sandbox had **zero** rows, so the per-record field names are **unvalidated**. Verify against
  Vici's real memberships/invoices onsite (the `rest`/remaining-balance field especially).
- **Service category/location:** not in the API (see ASK). Family view columns stay blank until we
  find the source.
- **Booking cancellations:** detection is unverified pending the approval/cancellation answer above.
- **Client `created_date`:** absent from the API → `createdAt` is null (harmless).

---

## Fast verification (do this per item, live)

We have a throwaway harness that hits each endpoint and diffs the real fields against what the code
reads. Once Vici's keys are set, re-run it to confirm each shape in seconds instead of guessing —
it's how every ✅ above was proven. Re-run after any credential/attribute-name change.

---

## Post-onsite (Render / prod)
- [ ] Confirm the first real **Sync Now** succeeds end-to-end (all steps green).
- [ ] Spot-check the dashboard: Overview, Students, Associations, Tutors all render with real data.
- [ ] Confirm EXT_ID + Account_ID populate on the Students/Associations pages.
- [ ] Merge the open fix branches (`booking-adapter-real-shapes`, `brevo-extid-attribute`).
