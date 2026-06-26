# Brevo Integration Subsystem Documentation | Group 15

## 1. Abstract & Operational Purpose
The Brevo communication subsystem serves as the **Voice** at the downstream end of the Vici Learning Integration Dashboard pipeline. While background synchronization modules pull operational records from SimplyBook.me into PostgreSQL, and the rules engine runs threshold calculations to isolate operational anomalies, this subsystem bridges those internal database flags into outbound administrative actions.

This module provides staff with a synchronized, interactive **Automations Review Queue**. It enables manual administrative verification of student parameters before forcing attribute updates to the CRM contact list or triggering live transactional email reminders.

---

## 2. Core Constraint: Relational to Flat CRM Architecture Mapping
A primary architectural hurdle in this subsystem is handling complex relational data loops inside Brevo's system capabilities under the constraints of the **Free Sandbox Tier**.

### The Relational vs. CRM Mismatch
* **Local Database Engine:** Handles multi-child families using true relational mapping definitions (1 Parent Account record can map to a separate list of multiple Students rows).
* **Brevo Free Sandbox Tier:** Lacks premium enterprise "Custom Objects" (marked by the Crown icon in the interface). It uses a single, flat Contact model where **one email address equals exactly one contact row**.

### Architectural Solution: Synchronized Parallel Arrays
To store tracking profiles for multiple children under a single parent email without causing duplicate message dispatches to the same client inbox, this application implements **Array-Based Attribute Flattening**. Native Java collections (`List<String>` and `List<LocalDate>`) are collapsed into predictable, ordered, pipe-delimited strings (`|`) before executing outbound API requests.

By ensuring your rules engine loops through a parent's children in a strictly symmetrical index position, the Brevo dashboard handles the flat text segments safely as parallel arrays:

| Positional Index | `STUDENT_NAMES` Attribute (Text) | `LAST_BOOKING_DATE` Attribute (Text) |
| :---: | :--- | :--- |
| **`Index [0]`** | Miata Boy | 2026-05-24 |
| **`Index [1]`** | Miata Girl | 2026-06-06 |

* **Resulting CRM String View:** `Miata Boy | Miata Girl`
* **Resulting CRM Date View:** `2026-05-24 | 2026-06-06`

> ⚠️ **CRITICAL ATTRIBUTE DATATYPE REQUIREMENT:** The `LAST_BOOKING_DATE` column field **must be configured as a TEXT type attribute** within the Brevo Dashboard rather than a standard Date type. If left as a Date type field, Brevo's API routers will reject the pipe-delimited text arrays with a validation error.

---

## 3. Custom CRM Column Layout (Contact Attributes)
To support transparency when staff log directly into the Brevo platform, you must click the **"Select attributes ⊕"** menu on your contact table and check the following custom fields:

| Attribute System Key | UI Display Type | Field Function & Mapping Context |
| :--- | :--- | :--- |
| `VICI_ACCOUNT_ID` | **Text** | The manual string identifier bridge linking CRM data back to the tracker sheet schema (e.g., `OV30174`). |
| `STUDENT_NAMES` | **Text** | Collapsed text array containing child names separated by structural pipes (`\|`). |
| `PAYMENT_STATUS` | **Text** | Credit-agnostic operational flag tracking billing states (e.g., `Good Standing`, `OVERDUE`). |
| `LAST_BOOKING_DATE` | **Text** | Parallel text array tracking independent student lesson recency timelines. |

---

## 4. Subsystem Code & File Architecture
The communication package utilizes a decoupled **Mock Provider Pattern** for this standalone development sprint. This allows full visual and network verification without introducing dependencies or merge risks to your teammates' active PostgreSQL schema branches.

```text
ca.vicilearning.dashboard
├── config
│   └── BrevoConfig.java                -> Instantiates the global RestClient bean with target authorization header keys.
├── comms
│   └── BrevoCommunicationService.java  -> Core network client engine managing outbound PUT (CRM Sync) and POST (SMTP) JSON payloads.
├── rules
│   ├── BrevoReviewTask.java            -> Unified domain data record formatting raw Lists into flat parallel pipe strings.
│   └── MockTaskService.java            -> Seed provider generating realistic multi-child and billing exception test targets.
└── web
    └── BrevoController.java            -> Orchestrates Thymeleaf model mappings, form extraction, and redirect filters.
```
---


## 5. End-to-End Execution Lifecycle
1. Queue Page Request (GET /comms/review): The BrevoController requests unaddressed issues from MockTaskService and pushes them down the Thymeleaf pipe.
2. Dynamic UI Rendering: The comms-review.html page uses standard system styling conventions to display the targets. The record's formatting helpers execute behind the scenes to bundle name and date arrays into flat strings.
3. Hidden Form Packaging: The Thymeleaf markup binds the transformed properties into hidden form parameters within the operational data row.
4. Administrative Confirmation (POST /comms/approve): When a staff member clicks "Approve and Send", the client browser submits the pre-flattened payload to the controller endpoint.
5. Horizontal CRM Sync: The BrevoCommunicationService initiates a PUT request to /contacts/{email} to align the sandbox dashboard columns live.
6. Transactional SMTP Dispatch: The service drops a POST request to /smtp/email, matching the correct Template ID and supplying the key-value dictionary tokens needed to compile the email.

---

## 6. Troubleshooting and Sandbox Limit Safeguards
* Daily Transaction Limits: Free sandboxes are tightly restricted to 300 emails per day. The BrevoCommunicationService error interceptor checks for client response patterns to explicitly catch 429 Too Many Requests rate caps, safely tracking the failure in the system log without hanging active server execution loops.
* Quotas Preservation: Mock arrays inside MockTaskService should remain bounded during development to prevent loop escalations from consuming your group's testing quota.