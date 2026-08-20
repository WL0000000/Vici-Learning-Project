# Handoff Notes - Vici Learning Dashboard

For whoever picks this up next: new teammate, next course group, whoever Sara hires. README has the pitch and course requirements. This is the "here's what'll actually trip you up" doc.

## What this is

Internal admin tool for Vici Learning, a private tutoring company. Their data lives in three systems that don't talk to each other: SimplyBook.me (bookings, tutors, invoices, memberships), Brevo (real student roster, contact info, email), Notion (tutor HR stuff). Staff used to reconcile all this by hand, a few hours a week. This app syncs SimplyBook.me and Brevo into one local DB on a schedule, computes the flags that matter (lapsed, owes money, low on sessions), and gives staff one place to work from.

Also a Tutor Portal, a read-only view for tutors to see their own schedule/students, plus an admin approval flow for new tutor accounts.

## Stack

- Java 21, Spring Boot 3.5
- Thymeleaf, no frontend framework, plain JS where needed
- PostgreSQL via Flyway migrations (`src/main/resources/db/migration`), never Hibernate auto-DDL
- Spring Security, three roles: `ADMIN`, `STAFF`, `TUTOR`
- Maven
- Tests run against H2, not Postgres

## Running it

1. `docker compose up -d` for Postgres.
2. Copy `.env.example` to `.env`, fill in real values. Don't commit `.env`.
3. `./mvnw spring-boot:run`

Two things that'll get you:

**Default datasource URL is hardcoded to port 5432.** If something else is already on that port, `docker compose up -d` just fails to bind. Free the port or override: `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:<port>/vici_dashboard ./mvnw spring-boot:run`.

**Two ways to run this without touching real client data.** `SPRING_PROFILES_ACTIVE=seed` gives you a fresh DB with fake data from `MockDataSeeder` and turns off the hourly sync scheduler entirely; use this for UI work or demos. Real credentials in `.env` plus a normal run gets you real synced data, no seed profile needed. The seeder skips itself if the DB already has students, but nothing stops a real sync from running against a seeded DB. Don't mix these up.

## The three systems

### SimplyBook.me

Two APIs, easy to forget: old JSON-RPC (tutors, services, clients, bookings) and REST v2 (custom fields, memberships, invoices). If sync fails with `error -32000: User with this login and password not found`, it's bad or stale creds, not code. Check that first.

Quirks the code already handles:

- Client list (`Student` entity) is duplicated, stale, not the real roster. Use `RosterStudent` (from Brevo) for actual student counts.
- Booking names are under the *parent's* name, kids in parentheses: `"Julie Gray (Hannah & Kaylee)"`. `TutorPortalDataService` has a heuristic matcher for this.
- Session times are plain local strings, no timezone, and represent Pacific (where Vici operates). We don't convert them.

### Brevo

Real student roster (`RosterStudent`, one row per child, keyed by `EXT_ID`). Email sends through their transactional API using templates you build in their dashboard, referenced by numeric ID.

Custom attributes the code depends on:
- `CONTACT_TYPE`: marks "Student." List/category type, not a string.
- `CONTACT_STATUS`: real enrollment status, Active/Paused/Dropped/Completed. Also category type.
- `ASSIGNED_TUTOR`: primary tutor. Plain string, but messy, see below.
- `EXT_ID`: per-student ID, read as a custom attribute (Brevo doesn't surface `ext_id` at the top level of list responses like you'd expect).

Don't assume a Brevo field's type from its name. Had a field declared `String` in the Java model that actually comes back as a category/array type on Vici's account, and it crashed parsing of the *entire* contacts page silently, so every sync looked successful with zero data. If you add a new attribute, check what it actually comes back as against a live pull first.

`ASSIGNED_TUTOR` is not reliably a clean name. August audit found:
- First name only (`"Ashman"` not `"Ashman Grewal"`)
- Comma-separated multiples (`"Sara, Ashman"`, covered by more than one tutor over time)
- The literal unresolved `"{{ deal.assigned tutor }}"` on a growing number of contacts. A broken Brevo automation on Vici's side, not fixable from our end. Needs to go to Sara's team. Worth rechecking before any handoff meeting, count went from 5 to 11 over about two weeks last time I looked.

`assignedTutorMatchesTutor()` in `TutorPortalDataService` handles the first two. Can't fix a genuine spelling mismatch or the broken merge tag, that's data correction on Brevo's side.

Brevo v3 API keys start with `xkeysib-`. No prefix means it's probably the wrong credential type (SMTP key instead of REST).

### Notion

Tutor data only, HR stuff: subjects, role, start date, admin notes. No student data in Notion, on purpose. Fetched live every page load, not synced to the DB, no scheduled job. Notion down or misconfigured means the page shows an empty state instead of erroring out.

## How the data links up

This caused the most bugs.

**Family/account linking.** `Surname_Account` on SimplyBook.me, Brevo Company name (free text, sometimes just `"Surname"`). `AccountIdNormalizer` compares the two formats, don't assume they're spelled the same.

**Student identity isn't shared between systems.** `Student` is the SimplyBook.me booking record. `RosterStudent` is the real student from Brevo. No shared numeric ID. Matched by name, tolerantly, via `NameNormalizer` (whitespace collapsing, including non-breaking spaces) plus family account key.

**Tutor assignment comes from Brevo's `ASSIGNED_TUTOR`, not booking history.** Deliberate: Sara pointed out a tutor subbing one session for someone else's regular student shouldn't show up as "their" student. So My Students in the Tutor Portal is driven entirely by that field, matched against `Tutor.name`, with the fallback matching above.

**Session/hour counts still come from actual bookings**, matched by the parent-name-vs-child-name heuristic. Separate match from assignment. Possible for assignment to be correct and session count to show zero if the booking-name heuristic doesn't catch the pattern; if you see that, check this path first.

## Timezone

Everything computes "now"/"today" from `AppClock.ZONE` (`America/Vancouver`), not UTC, not server default. SimplyBook.me times are naive local values already in Pacific, so comparing against UTC "now" would be silently off by 7-8 hours. Before this fix, payment-reminder and lapse-detection used a bare `LocalDateTime.now()`. Worked fine locally because the dev machine happened to be on Pacific time, would've broken the moment it ran on a UTC server. Any new date/time comparison against booking data: use `AppClock.ZONE`.

Old sync logs and alert records from before this fix are under the old UTC convention, will look off by a few hours next to newer rows until they age out. Not a bug, just leftover.

## Roles

- `ADMIN`: full access, only role that can approve/reject tutor registrations.
- `STAFF`: same as ADMIN minus account approval. For senior tutors/office staff.
- `TUTOR`: `/tutor-portal/**` only, view-only, own students/schedule.

New tutors self-register at `/register`, sit pending until an admin approves at `/admin/users`. Overview page has a pending-count banner for admins. `BrevoCommunicationService.notifyAdminOfPendingTutor` exists but needs a real `BREVO_TUTOR_APPROVAL_TEMPLATE_ID`; without one it logs and skips silently instead of failing.

## Where things are

- **Overview** (`/`): `HomeController`, `index.html`. Daily snapshot, pending invoices, pending approvals.
- **Students & Hours** (`/students`): `StudentsController`, `students.html`. Brevo roster joined with SimplyBook.me hours, filterable by week/month/year/date range, location, category.
- **Tutors** (`/api/notion/tutors`): `NotionController`, `notion-tutors.html`. Live from Notion.
- **Associations** (`/associations`): `AssociationController`/`AssociationService`. Family grouping.
- **Automations** (`/comms/review`): `BrevoController`, `AutomationsQueueService`, `comms-review.html`. Three queues: lapse discrepancies, session-pack renewals, payment reminders (2-week/72-hour/12-hour tiers). Nothing auto-sends, Sara wants a staff click, not full automation.
- **Sync Status** (`/sync`): `SyncController`, `SyncService`. Manual trigger plus run history with errors.
- **Users & Approvals** (`/admin/users`): `UsersController`. Admin only.
- **Tutor Portal** (`/tutor-portal/**`): `TutorOverviewController`, `TutorBookingsController`, `TutorStudentsController`, backed by `TutorPortalDataService`.

## Open items

- `ASSIGNED_TUTOR` merge-tag bug: live, growing, client-side. Flag early in any handoff conversation.
- Payment/renewal emails need real Brevo templates before they'll send. Buttons show disabled with an explanation until then.
- Tutor status (Active/Inactive/Legacy) is computed on the backend but has no admin UI yet. Scope decision, wanted the data model right first.
- Name matching is heuristic, not ID-based, because nothing shares an identifier. Hardened against whitespace, casing, parent-vs-child, first-name-only, but a real spelling difference or new pattern can still slip through. If something's unmatched, check Brevo/SimplyBook.me field values before assuming it's a code bug.
- No due-date field on invoices, only issued date. Overdue logic runs off "unpaid plus session coming up," not a calendar date.

## Testing

`./mvnw test`: H2, no external calls. For real-data bugs, `psql` directly against local Postgres is usually faster than adding print statements. Most bugs in this project were visible immediately in the raw data once you knew where to look.
