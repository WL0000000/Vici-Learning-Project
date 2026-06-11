# Vici Learning Integration Dashboard | Group 15

Internal operations and automation dashboard for Vici Learning 

**Client:** Sarah Alhower, Vici Learning · **Course:** CMPT 276 


## General Rules

- Everyone works on their own separate branch
- NEVER work on and push code straight to main branch
- ONLY push working/reviewed code to main branch
- Frequently communicate pushes
- Always pull whenever someone pushes. This avoids the headache of outdated local branch & merge conflicts
- Communicate what feature/component/section you're working on so others don't touch it
- **Never commit real client data or API keys**. Sandbox accounts and mock data only

## Tech Stack 

-Java 21 · Spring Boot 3 · Maven · Thymeleaf (server-rendered HTML, no frontend frameworks) ·
PostgreSQL via Docker Compose · SimplyBook.me JSON-RPC client · Brevo Java SDK.


## How to First Initialize & Run

```
1. Install Java 21:
Download: https://adoptium.net/ → Temurin 21 LTS

2. Install Maven:
Download: https://maven.apache.org/download.cgi

For both of the above, extract folder and copy them as path to your user environment variables inside path and system variable path.

3. Start the database:
Run: **docker compose up -d**
Docker Desktop must be running

4. Run the app:
mvn spring-boot:run

5. Open browser:
Go to http://localhost:8080
TEST Login: username: admin pass: changeme
6. Run tests (uses in-memory H2, no Docker needed)

mvn test
docker compose up        # database
mvn spring-boot:run      # application
```

Secrets go in `.env` (never committed). `.env.example` will list the required keys.
