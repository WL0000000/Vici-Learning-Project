package ca.vicilearning.dashboard.auth;

/**
 * Login user types for role-based access. Distinct from the synced SimplyBook
 * {@code Tutor} entity: that's a service provider pulled from SimplyBook, whereas a
 * {@code TUTOR} here is a staff/tutor <em>login account</em> with a restricted view.
 *
 * <ul>
 *   <li>{@code ADMIN} — full access (dashboard, sync, all students, user management).</li>
 *   <li>{@code TUTOR} — standard user; the default for self-registration.</li>
 * </ul>
 */
public enum Role {
    ADMIN,
    TUTOR;

    /** Spring Security authority name, e.g. {@code ROLE_ADMIN}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
