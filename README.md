# Vici Learning Integration Dashboard | Group 15

Internal operations and automation dashboard for Vici Learning that centralizes administrative
data from SimplyBook.me (bookings) and Brevo (CRM/email), surfaces actionable follow-up tasks,
and triggers automated communication, replacing the manual spreadsheet reconciliation the staff
does today.

**Client:** Sarah Alhower, Vici Learning · **Course:** CMPT 276 (June–August 2026)


## General Rules

- Everyone works on their own separate branch
- NEVER work on and push code straight to main branch
- ONLY push working/reviewed code to main branch
- Frequently communicate pushes
- Always pull whenever someone pushes. This avoids the headache of outdated local branch & merge conflicts
- Communicate what feature/component/section you're working on so others don't touch it
- **Never commit real client data or API keys** — sandbox accounts and mock data only

## Tech Stack (summary)

-Java 21 · Spring Boot 3 · Maven · Thymeleaf (server-rendered HTML, no frontend frameworks) ·
PostgreSQL via Docker Compose · SimplyBook.me JSON-RPC client · Brevo Java SDK.


## Quick Start (placeholder — Phase 0)

```
# coming in Phase 0:
docker compose up        # database
mvn spring-boot:run      # application
```

Secrets go in `.env` (never committed). `.env.example` will list the required keys.
