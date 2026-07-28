package ca.vicilearning.dashboard.auth;

public enum Role {
    ADMIN,
    STAFF,
    TUTOR;

    public String authority() {
        return "ROLE_" + name();
    }
}