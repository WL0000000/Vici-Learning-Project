# Vici Learning Integration Dashboard: Iteration 1 Report

**Course:** CMPT 276, Group 15
**Client:** Sarah Alhower, Vici Learning

## Submission Info

| Item | Link / Value |
|------|--------------|
| Git repository | https://github.com/WL0000000/Vici-Learning-Project |
| Live web app (Render) | https://vici-learning-project.onrender.com |
| Admin login | username `Admin`, password `ViciLearning2026` |
| Screencast | see `docs/` (or the link submitted alongside this report) |

Tutor accounts are created through the registration page, so a marker can also sign up as a new user and see the restricted view.

## 1. What the Project Does

Vici Learning is a tutoring company that runs its day to day work across a few separate tools: SimplyBook.me for bookings, Brevo for email and contacts, and Notion for the tutor list. None of these talk to each other, so staff spend hours each week exporting files and lining them up by hand in Excel. Our dashboard sits on top of those tools, pulls their data into one Postgres database on a schedule, and gives staff a single place to check bookings, find follow up work, and manage students.

For iteration 1 we focused on the account system (registration, login, logout, and the split between admin and tutor views) and on our most important feature, which is the SimplyBook sync and the dashboard that reads from it. Login was written from scratch. We store our own accounts and hash the passwords ourselves rather than using a third party sign in.

## 2. How We Worked

The team is five people and each person owns one of the five epics from our proposal (authentication and roles, SimplyBook integration, dashboard and analytics, the rules engine, and Brevo communications). Everyone works on their own branch and nobody pushes straight to `main`. Code gets in through pull requests that another teammate looks over, and we post in the group chat whenever something is merged so people remember to pull. We also say which area we are touching before starting so two of us are not editing the same file at once.

We kept issues on GitHub for the bigger tasks and used PR comments for review back and forth. The commit history and PR list show the split of work across the five of us.

## 3. Personas

**Jane, office administrator.** Jane runs the front office at Vici. She books sessions, chases up families who have not paid, and needs the full picture across every student. She is comfortable with software but does not want to babysit spreadsheets. She maps to the `ADMIN` role.

**Joe, tutor.** Joe teaches a handful of students each week. He wants to see who he is working with and how many hours are on the books, but he has no reason to see a family's billing account or phone number, and he should not be touching the sync or the email tools. He maps to the `TUTOR` role.

## 4. User Stories

Every story below follows the same shape: a persona, the action they want, and the result they get. Each one lists a success case and a failure case so the behaviour is testable both ways.

### Story 1: Register an account
As a new staff member, I want to create an account with a username and password so that I can get into the dashboard without an admin having to set it up for me.

- **Success:** I enter a username that is not already taken and a password of at least 8 characters, then confirm the password. The account is saved and I land on the login page with a message saying the account was created. New accounts get the tutor role by default.
- **Failure:** If the username is already taken, the two password fields do not match, or the password is shorter than 8 characters, the page reloads and shows the reason. My username stays filled in, but the password fields are cleared and the password is never sent back to the browser.

### Story 2: Log in
As a registered user, I want to sign in with my username and password so that I can reach the pages my role allows.

- **Success:** I type the right username and password and get taken to the overview page.
- **Failure:** If I get the password wrong or type a username that does not exist, the login page reloads with "Incorrect username or password." The message does not say which of the two was wrong, so it gives nothing away.

### Story 3: Log out
As a signed in user, I want to log out so that the next person on the same computer cannot use my session.

- **Success:** I click Log out, my session ends, and I am returned to the login page with a note confirming I logged out.
- **Failure:** After logging out, if I try to open a protected page such as `/students`, the app sends me back to the login page instead of showing the content.

### Story 4: Full admin access
As an office administrator (Jane), I want access to every page so that I can run syncs, review the email queue, and see complete student records.

- **Success:** When Jane logs in, the sidebar shows every section including Sync Status and Automations, and the students table includes the Account ID, email, and phone columns.
- **Failure:** If Jane's session has expired or she is not signed in, she is treated like any visitor and gets redirected to the login page before any admin page loads.

### Story 5: Restricted tutor view
As a tutor (Joe), I want to see the students and dashboard but be kept out of the admin tools so that I only deal with what my role needs.

- **Success:** Joe signs in and can open the overview and students pages. The Sync Status and Automations links are not in his sidebar at all.
- **Failure:** If Joe types `/sync` or `/comms/review` straight into the address bar, the app returns a 403 access denied response rather than loading the page. The block is enforced on the server, not just hidden from the menu.

### Story 6: Keep sensitive student info admin only
As the client, I want tutors to see student names and hours but not family contact or billing details so that private information stays with admins.

- **Success:** On the students page a tutor sees the name, weekly sessions, and hours for each student. The Account ID, email, and phone columns are not shown.
- **Failure:** An admin opening the same page still sees those three columns, which confirms the hiding is tied to the role and was not just removed for everyone.

### Story 7: View dashboard metrics
As an administrator, I want the dashboard to show weekly hours, active student counts, and upcoming sessions worked out from our own database so that I can answer questions without exporting spreadsheets.

- **Success:** The page loads charts and tables built from the local data. Hours come from each booking's real duration, so a two hour session counts as two hours and not one.
- **Failure:** With an empty database the page still loads and shows zeros or a "no data" note instead of throwing an error.

### Story 8: Sync data from SimplyBook
As an administrator, I want to pull bookings, clients, services, tutors, invoices, and memberships from SimplyBook into our database so that every page reads fast local data.

- **Success:** I click Sync Now and the sync runs. The status page shows updated counts and a success flag. Each part of the sync runs in its own transaction so a half finished step never leaves messy data behind.
- **Failure:** If one upstream call fails, for example the SimplyBook API is down, that step is marked failed and written to the log, the other steps still finish, and the run is recorded as not fully successful. One bad step does not wipe out the whole sync.

## 5. UI Mockups and Screenshots

The login and registration pages are already built, so the screenshots of the real pages act as the mockups for those stories. The full-size images live in the `docs/` folder.

**Login page** (with the error and "account created" banners in Story 1 and 2):

![Login page](docs/01-login.png)

**Registration page** (Story 1):

![Registration page](docs/02-register.png)

The clearest thing to show is the difference between the two roles, since that is the part a reader cannot guess from words alone.

**Students page as an admin**, with the Account ID, email, and phone columns visible (Story 4 and 6):

![Students page as admin](docs/03-students-admin.png)

**Students page as a tutor**, where those columns are gone (Story 5 and 6):

![Students page as tutor](docs/04-students-tutor.png)

The sidebar also changes by role. Sync Status and Automations show for an admin and disappear for a tutor.

**Admin sidebar:**

![Admin sidebar](docs/05-sidebar-admin.png)

**Tutor sidebar:**

![Tutor sidebar](docs/06-sidebar-tutor.png)

**Overview dashboard** with the live metrics and upcoming sessions (Story 7):

![Dashboard](docs/08-dashboard.png)

**Sync status page** where an admin runs and monitors the SimplyBook sync (Story 8):

![Sync status](docs/07-sync.png)

## 6. Testing Notes

The stories above are backed by automated tests as well as manual checks. On the account and access side we test that a tutor gets a 403 on the sync and automations pages, that an admin gets those pages fine, that a tutor can still open the students page, and that the sensitive columns show up for an admin but not for a tutor. On the sync side we test that a failing step does not stop the others and that missing records are handled cleanly. Password handling is checked with a real hashing library so we know passwords are stored hashed and never in plain text.

## 7. Retrospective

Iteration 1 was mostly about getting a workflow the whole team is happy with. The branch per person plus pull request setup worked well and kept merge conflicts small. The main thing that slowed us down was schema drift between our local databases, where someone would add a column and another person's database would not have it yet. For iteration 2 we want to smooth that out, either by rebuilding the database more deliberately when the schema changes or by bringing in a migration tool. We also want to move the role based views further so a tutor eventually sees only their own students rather than the whole list with sensitive columns removed.
