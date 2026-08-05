package ca.vicilearning.dashboard.domain;

/**
 * A student's enrolment status. The source of truth is Brevo's {@code CONTACT_STATUS} attribute
 * (confirmed onsite 2026-07-30 — NOT {@code STUDENT_STATUS}), read from the Brevo student record on
 * the roster ({@code RosterStudent}). Sara's Brevo uses exactly these four values:
 *
 * <ul>
 *   <li>{@code ACTIVE} — currently enrolled and booking. The default.</li>
 *   <li>{@code PAUSED} — temporarily not enrolled (e.g. a break), still on the books.</li>
 *   <li>{@code DROPPED} — left; no longer a student.</li>
 *   <li>{@code COMPLETED} — finished their programme.</li>
 * </ul>
 *
 * <p>ACTIVE + PAUSED are the "current" roster; DROPPED + COMPLETED are past students (filtered out
 * of the current view by default). Distinct from the computed "lapsed" concept (booking recency).
 */
public enum StudentStatus {
    ACTIVE,
    PAUSED,
    DROPPED,
    COMPLETED;

    /** True for statuses that count as a current student (shown on the default roster). */
    public boolean isCurrent() {
        return this == ACTIVE || this == PAUSED;
    }

    /** The Brevo CONTACT_STATUS string for this status (proper case, e.g. ACTIVE → "Active"), for write-back. */
    public String brevoValue() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }

    /** Tolerant parse of a Brevo CONTACT_STATUS value (e.g. "Active" → ACTIVE); null when unrecognized. */
    public static StudentStatus fromBrevo(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return StudentStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
