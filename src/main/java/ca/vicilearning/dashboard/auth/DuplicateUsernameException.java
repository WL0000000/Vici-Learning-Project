package ca.vicilearning.dashboard.auth;

/** Thrown when registration is attempted with a username that already exists. */
public class DuplicateUsernameException extends RuntimeException {
    public DuplicateUsernameException(String username) {
        super("Username already taken: " + username);
    }
}
