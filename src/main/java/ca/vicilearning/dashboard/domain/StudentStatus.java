package ca.vicilearning.dashboard.domain;

/**
 * A student's enrolment status — a manual, staff/client-set flag distinct from the "lapsed" concept
 * (which is computed from booking recency). Confirmed by Sara in Meeting #4: the dashboard must let
 * staff distinguish and filter students who are actively enrolled from those on pause.
 *
 * <ul>
 *   <li>{@code ACTIVE} — currently enrolled and booking. The default for every student.</li>
 *   <li>{@code PAUSED} — temporarily not enrolled (e.g. a break), still on the books.</li>
 * </ul>
 *
 * <p>The source of truth is ultimately Brevo's {@code STUDENT_STATUS} attribute; until that is
 * synced, the value is seeded/held locally and survives each SimplyBook sync (SimplyBook doesn't
 * carry it).
 */
public enum StudentStatus {
    ACTIVE,
    PAUSED
}
