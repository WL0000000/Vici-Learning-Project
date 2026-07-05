package ca.vicilearning.dashboard.auth;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Handles account creation (registration) and supplies user records to Spring Security at
 * login time via {@link UserDetailsService}. Passwords are BCrypt-hashed here and only ever
 * stored/compared as hashes.
 */
@Service
public class AppUserService implements UserDetailsService {

    static final int MIN_PASSWORD_LENGTH = 8;

    private final AppUserRepository repo;
    private final PasswordEncoder encoder;

    public AppUserService(AppUserRepository repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    /** Self-service registration: new accounts get the standard {@link Role#TUTOR} role. */
    public AppUser register(String username, String rawPassword) {
        return register(username, rawPassword, Role.TUTOR);
    }

    /**
     * Creates an account with the given role. Trims the username, rejects blanks, enforces a
     * minimum password length, and rejects duplicates. The password is stored BCrypt-hashed.
     *
     * @throws IllegalArgumentException   if the username is blank or the password too short
     * @throws DuplicateUsernameException if the username already exists
     */
    public AppUser register(String username, String rawPassword, Role role) {
        String name = username == null ? "" : username.trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (repo.existsByUsername(name)) {
            throw new DuplicateUsernameException(name);
        }

        AppUser user = new AppUser();
        user.setUsername(name);
        user.setPassword(encoder.encode(rawPassword));
        user.setRole(role);
        user.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return repo.save(user);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = repo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No user named " + username));
        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority(user.getRole().authority())))
                .build();
    }
}
