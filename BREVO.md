# Brevo Integration Subsystem Documentation | Group 15

## How to setup Brevo for local instance:

To integrate your Brevo account with this dashboard program, you need to generate a v3 API key from your Brevo workspace and add it to your local environment configurations.

Step 1: Generate the API Key in Brevo
1. Log into your Brevo sandbox account.
2. Click on the settings gear in the top right corner of the dashboard panel dashboard interface.
3. Select SMTP and API from the dropdown options layout menu.
4. Click on the Generate a new API key button.
5. Provide a recognizable name for the key, such as Vici Dev Sandbox, and click Generate.
6. Copy the full API key string immediately. Note that Brevo will obscure this value permanently once you close the pop up window, so ensure you capture it safely.

Step 2: Configure the Program Properties
1. Open the project source code explorer inside your local development environment.
2. Navigate to .env
3. Add the following configuration line to the text block, replacing the placeholder with your live copied credentials:
`BREVO_API_KEY=your_copied_api_key_here`

---

## 1. System Integration Overview

The VICI Learning dashboard integrates directly with the Brevo CRM v3 API Engine to achieve automatic, two-way reconciliation between local academic booking timelines and client communication states.

The primary operational goal is to flag synchronization anomalies—such as a student whose localized booking pattern has ceased but remains marked as "Active" inside the marketing ecosystem. This ensures automated communication flows match actual student behavior without creating manual administrative overhead.

```
+------------------------+             +------------------------+
|   VICI Local DB        |             |   Brevo Remote CRM     |
|  (Bookings & Students) |             | (Contact Segments/CSV) |
+-----------+------------+             +-----------+------------+
            |                                      |
            |      [1. Bulk In-Memory Fetch]       |
            +<-------------------------------------+
            |                                      |
            v                                      |
+------------------------------------+             |
|    BrevoSyncEngineService          |             |
|  - Compares 14-day calendars       |             |
|  - Validates parallel CSV tokens   |             |
+-----------+------------------------+             |
            |                                      |
            v                                      |
+------------------------------------+             |
|    AlertStudent Repository         |             |
|  - Identifies row discrepancies    |             |
|  - Drives the visual view engine   |             |
+-----------+------------------------+             |
            |                                      |
            |    [2. Target Update Calls]          |
            +------------------------------------->+

```

---

## 2. Architecture & Class Directory

### Core Configuration Layer

- `BrevoConfig.java` Initializes and injects a thread-safe Spring `RestClient` bean. Abstractly attaches global context headers (`Content-Type`, `Accept`, and the private token key `api-key`) and establishes base service locations.

### Integration & Data Ingestion Layer

- `BrevoCommunicationService.java` The primary network interaction bridge. Implements structured, high-speed Jackson data records (`BrevoContactNode`, `BrevoAttributesNode`, `BrevoListContactsResponse`) to ingest payload structures safely. It encapsulates specific API transactions, custom attributes updates, and transactional SMTP template dispatches.

### Domain Validation Layer

- `AlertStudent.java` Database entity tracking the live status of individual students. Uses the student's normalized `name` as the persistent look-up key registry.

- `AlertStudentRepository.java` Extends `JpaRepository`. Provides dedicated account aggregation scopes and exposes a optimized JPQL query (`findDiscrepancies`) mapping visual variance flags where local states conflict with remote marketing logs.

### Execution Processing Engine

- `BrevoSyncEngineService.java` A automated cron manager running hourly. It pulls global student parameters in a single block to minimize network latency, unpacks complex string payloads, computes active timeline states, and executes atomic upserts against anomalous entities.

---

## 3. Data Mapping & Property Specifications
The synchronization pipeline links local relational data grids with Brevo's unstructured custom contact layout keys.

| Brevo CRM Property Key | Data Representation | Core System Usage | Local Mapping Field |
| :--- | :--- | :--- | :--- |
| `VICI_ACCOUNT_ID` | `String` (Normalized Key) | Unique parent/family grouping index | `AlertStudent.accountId` |
| `STUDENT_NAMES` | `String` (Comma-Separated) | Multi-student index array | `AlertStudent.name` (Split) |
| `ACTIVITY_STATUS` | `String` (Comma-Separated) | Corresponding status states array | `AlertStudent.lapsedStatus` |
| `LAST_BOOKING_DATE` | `String` (ISO Timestamp) | Chronological validation target | Evaluated via local parameters |

### Crucial Parallel Token Schema Requirement

The attributes `STUDENT_NAMES` and `ACTIVITY_STATUS` function as parallel indices mapped via comma-separated string streams:

- **Example Value Matrix**:

  - `STUDENT_NAMES`: `"Alex Smith, Taylor Smith"`

  - `ACTIVITY_STATUS`: `"Active, Lapsed"`

- **Resolution Pattern**: The synchronization processor parses index `0` (`alex smith`) as `Active`, and index `1` (`taylor smith`) as `Lapsed`. If a status index is absent, the system defaults the entity configuration state to `"Active"`.

---

## 4. Operational Logic & Rules Configuration

### Timeline Evaluation Engine (Lapse Calculations)

A student is flagged as `lapsedNow = true` if they have no valid engagement records inside a 14-day time window. The logic evaluates trailing histories and future schedules:


$$\text{Lapsed State} = \left( \text{No Converted Bookings in past 14 days} \right) \;\land\; \left( \text{No Upcoming Bookings scheduled in the future} \right)$$

- Exclusion Matrix: Bookings marked as soft-deleted (`deletedAt != null`) or explicitly tagged as `"cancelled"` are dropped immediately and do not reset the tracking baseline window.

### Network Call Defenses (N+1 Prevention)

To maintain quick processing execution and stay well within Brevo rate limits, the system avoids issuing individual validation lookups inside loops:

* **Dynamic Page-Streaming Streams:** Rather than firing a single hardcoded request restricted to the first 100 entries (`limit=100&offset=0`), the cron routine dynamically page-streams the Brevo `/contacts` endpoint. It leverages an iterative loop block that reads the cumulative metadata `count` attribute returned in the API envelope, shifting the query `offset` sequentially until the entire remote payload has been safely consumed.
* **Hierarchical Namespace Ingestion:** To prevent global string key collisions when entirely separate families register students with identical names, the ingested contact properties are mapped into a multi-tiered structural directory layout in memory: `Map<String, Map<String, String>>`.
* **Account-Scoped Mapping Isolation:** The master structure indexes each unique family grouping by its normalized parent identifier (`VICI_ACCOUNT_ID`), which maps directly to an isolated nested sibling dictionary containing keys for `Map<LowercaseStudentName, ActivityStatus>`.
* **$O(1)$ Local Computations:** Local evaluation loops execute state validation checks directly against this scoped inner memory grid. This architecture limits cross-network load to a single, optimized data ingestion phase during startup and isolates multi-child parallel tokens.
---


## 5. API & Endpoint Reference Guide

All outbound connections use the secure root base path set by `${brevo.api.url:https://api.brevo.com/v3}`.

`GET /contacts`

- **Usage Context**: Automatically runs up-front inside synchronization procedures (`fetchViciIdToEmailMap`, `fetchStudentStatusMap`).

- **Query Parameters**: `limit=100&offset=0`

`PUT /contacts/{email}`

- **Usage Context**: Pushes metadata updates down to a parent's record field.

- **Payload Schema Matrix** (JSON):
```json
{
  "attributes": {
    "LAST_BOOKING_DATE": "2026-06-30T19:00:00Z",
    "ACTIVITY_STATUS": "Active, Lapsed"
  }
}
```

`POST /smtp/email`

- **Usage Context**: Dispatches customized automated notices using pre-built graphic communication layouts.

- **Payload Schema Matrix** (JSON):

```json
{
  "templateId": 42,
  "to": [
    {
      "email": "parent@example.com",
      "name": "Jane Doe"
    }
  ],
  "params": {
    "studentName": "Alex Doe",
    "daysSinceLastBooking": 14
  }
}
```

---

## 6. Error Handling, Logging, and Telemetry

The integration uses **SLF4J Logger abstractions** to replace standard print tracing, ensuring structured logs are easily parsed by modern monitoring systems.

### Resiliency Patterns
- **Exception Isolation**: Loop processes are isolated within explicit `try-catch` structures. A remote network timeout on a single student item won't interrupt the remaining batch operations.

- **Graceful Degradation**: If data mappings fail or return empty sets during unpacking cycles, target arrays fall back to safe default parameters (`"Active"`) to prevent null pointer exceptions.

- **Discrepancy Identification**: Conflicting records are continuously written to the `alert_student` data table, allowing management panels to query synchronization errors cleanly at any time:

```SQL
SELECT * FROM alert_student WHERE lapsed_now != lapsed_status;
```