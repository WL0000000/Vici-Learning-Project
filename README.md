# DRAFT
# Vici Learning Integration Dashboard — Group 15

Internal operations and automation dashboard designed for Vici Learning to centralize administrative data.

---

## 🛠️ Proposed Tech Stacks (Architectural Options)

### Option 1: Full-Stack Next.js (Recommended)
* **Frontend & Backend Framework:** Next.js 14+ (App Router) with TypeScript
* **Database & ORM:** PostgreSQL + Prisma ORM
* **UI & Styling:** Tailwind CSS + shadcn/ui components
* **Deployment:** Vercel (Frontend/API) + Supabase/Render (Database)
* **Pros:** Monorepo architecture simplifies deployment; compile-time type safety across third-party API data payloads prevents runtime sync issues.

### Option 2: Decoupled React + Express
* **Frontend:** Vite + React (TypeScript or JavaScript)
* **Backend:** Node.js + Express
* **Database:** MongoDB (via Mongoose)
* **Pros:** Complete separation of concerns between client and server layout; flexible NoSQL document structure absorbs unpredictable third-party JSON changes easily.

### Option 3: React + Python FastAPI
* **Frontend:** Vite + React 
* **Backend:** Python FastAPI
* **Database:** PostgreSQL (via SQLAlchemy)
* **Pros:** Excellent data manipulation libraries (`pandas`) for operational analytics; native asynchronous task queues (`Celery` + `Redis`) safely handle bulk transactional operations.

---

## 👥 Prosposed Team Roles & Responsibilities

### 1. Product Manager & Lead Client Liaison
* **Core Focus:** Requirement gathering, client communication, and backlog prioritization.
* **Responsibilities:**
  * Serves as primary contact for the client (Sarah Alhower).
  * Translates business workflow challenges into actionable technical tasks.
  * Owns dashboard UX design patterns, layout flows, and product verification.

### 2. Lead Backend Engineer (API Integration Specialist)
* **Core Focus:** Third-party integrations, webhook design, and data processing.
* **Responsibilities:**
  * Owns connectivity, validation, and authentication with SimplyBookMe, Notion, and Brevo APIs.
  * Builds scheduled script workers (cron jobs) to identify operational tasks like inactive/lapsed student profiles.
  * Enforces API rate-limiting strategies and custom error logging.

### 3. Database & DevOps Engineer (Systems Architect)
* **Core Focus:** Schema modeling, data integrity, caching, and infrastructure.
* **Responsibilities:**
  * Architectures relational tables or document structures to accurately cache external state.
  * Manages continuous synchronization logic to prevent data duplication.
  * Configures environment variables, CI/CD pipelines, and cloud platform hosting.

### 4. Frontend Engineer (UI/UX Developer)
* **Core Focus:** Accessible design systems, state hydration, and dashboard interfaces.
* **Responsibilities:**
  * Implements responsive administrative layouts, navigation systems, and metrics visualizations.
  * Constructs interactive, server-side sorted data tables with custom filtering mechanisms.
  * Optimizes UI paint performance and rendering states.

### 5. Full-Stack / Automation Engineer (Integration & State Bridge)
* **Core Focus:** Event-driven mechanics, frontend-backend hooks, and validation.
* **Responsibilities:**
  * Connects client-side user interactions directly to custom backend actions (e.g., triggering a Brevo sequence on user click).
  * Standardizes strict client-side form validations and global error alerts.
  * Coordinates shared components and internal state routing.

---

## 🚀 Environment Configuration
Copy `.env.example` into a local `.env` file before booting the development server. **Never commit actual API credentials to source control.**

```bash
# Example Only - Keep Local
DATABASE_URL="your-dev-database-connection-string"
SIMPLYBOOKME_API_KEY="your-sandbox-key"
NOTION_API_KEY="your-sandbox-key"
BREVO_API_KEY="your-sandbox-key"
