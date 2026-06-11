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

First Time Setup

1. Install Prereqs

install Java 21
RUN THIS ON WINDOWS POWERSHELL, or terminal if ur on mac: winget install EclipseAdoptium.Temurin.21.JDK

install Maven winget doesn't have it, install manually:
1. Download apache-maven-3.9.x-bin.zip from https://maven.apache.org/download.cgi
2. Extract it to C:\Users\<yourname>\Documents\apache-maven-3.9.x
3. Run this in PowerShell to add it to your PATH:
[System.Environment]::SetEnvironmentVariable("MAVEN_HOMments\apache-maven-3.9.x", "User")
$currentPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
[System.Environment]::SetEnvironmentVariable("Path", "$e>\Documents\apache-maven-3.9.x\bin", "User")
4. Close and reopen PowerShell, then verify: mvn -version

install Docker Desktop: https://www.docker.com/products/docker-desktop 
and launch it.

---
2. Run the App

Open PowerShell in the project folder and run these two

docker compose up -d
mvn spring-boot:run

If mvn is not recognized in an existing terminal, refresh the PATH first:
$env:Path = [System.Environment]::GetEnvironmentVariabl[System.Environment]::GetEnvironmentVariable("Path","User")

---
3. Open in Browser

http://localhost:8080

Login credentials:
- Username: admin
- Password: changeme

---
4. Stop the App

# Ctrl+C to stop Spring Boot
docker compose down   # stops the database

5. See changes on localhost:

if you make a change then Ctrl+C to stop spring boot if its a java file change. Then run mvn spring-boot:run again to see changes after refresh. 
If you make a html or css change then just refresh the page to see changes.


