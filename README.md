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


## Quick Start Guide

### First-time setup

**1. Install Java 21**

- Windows (PowerShell): `winget install EclipseAdoptium.Temurin.21.JDK`
- macOS / Linux: download Temurin 21 LTS from https://adoptium.net/

**2. Install Maven**

Maven is not available via winget — install manually:

1. Download `apache-maven-3.9.x-bin.zip` from https://maven.apache.org/download.cgi
2. Extract to `C:\Users\<yourname>\Documents\apache-maven-3.9.x` (adjust path on macOS/Linux)
3. Add Maven to your PATH (Windows PowerShell example):

```powershell
[System.Environment]::SetEnvironmentVariable("MAVEN_HOME", "C:\Users\<yourname>\Documents\apache-maven-3.9.x", "User")
$currentPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
[System.Environment]::SetEnvironmentVariable("Path", "$currentPath;C:\Users\<yourname>\Documents\apache-maven-3.9.x\bin", "User")
```

4. Close and reopen your terminal, then verify: `mvn -version`

On macOS/Linux, add the `bin` directory to your shell profile (`~/.zshrc` or `~/.bashrc`) instead.

**3. Install Docker Desktop**

Download from https://www.docker.com/products/docker-desktop and launch it before starting the database.

---

### Run the app

Open a terminal in the project folder:

```bash
docker compose up -d
mvn spring-boot:run
```

If `mvn` is not recognized in an already-open terminal, refresh PATH first (Windows PowerShell):

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
```

---

### Open in browser

http://localhost:8080

Login credentials:

- Username: `admin`
- Password: `changeme`

---

### Run tests

Tests use an in-memory H2 database — Docker is not required:

```bash
mvn test
```

---

### Stop the app

- `Ctrl+C` to stop Spring Boot
- `docker compose down` to stop the database

---

### See changes on localhost

- **Java changes:** stop Spring Boot (`Ctrl+C`), then run `mvn spring-boot:run` again
- **HTML or CSS changes:** refresh the page (Thymeleaf cache is disabled in dev)

Secrets go in `.env` (never committed). `.env.example` will list the required keys.
