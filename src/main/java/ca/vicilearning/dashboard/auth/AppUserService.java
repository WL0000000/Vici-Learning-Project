package ca.vicilearning.dashboard.auth;

import ca.vicilearning.dashboard.notion.NotionService;
import ca.vicilearning.dashboard.notion.NotionTutor;
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

@Service
public class AppUserService implements UserDetailsService {

    static final int MIN_PASSWORD_LENGTH = 8;

    private final AppUserRepository repo;
    private final PasswordEncoder encoder;
    private final NotionService notionService;

    public AppUserService(AppUserRepository repo, PasswordEncoder encoder, NotionService notionService) {
        this.repo = repo;
        this.encoder = encoder;
        this.notionService = notionService;
    }

    public RegistrationResult registerSelfService(String username, String rawPassword) {
        boolean isSeniorTutor = isSeniorTutor(username);
        Role role = isSeniorTutor ? Role.STAFF : Role.TUTOR;
        boolean approved = isSeniorTutor;

        AppUser user = register(username, rawPassword, role, approved);
        return new RegistrationResult(user, !approved);
    }

    public AppUser registerAsAdmin(String username, String rawPassword, Role role) {
        return register(username, rawPassword, role, true);
    }

    public AppUser register(String username, String rawPassword, Role role) {
        return register(username, rawPassword, role, true);
    }

    private AppUser register(String username, String rawPassword, Role role, boolean approved) {
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
        user.setApproved(approved);
        user.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return repo.save(user);
    }

    /** True if the username (email) matches a Notion tutor record marked Senior Tutor */
    private boolean isSeniorTutor(String username) {
        if (username == null || username.isBlank()) return false;
        try {
            List<NotionTutor> tutors = notionService.getTutorRows();
            return tutors.stream().anyMatch(t ->
                    t.email() != null && t.email().equalsIgnoreCase(username.trim())
                            && t.tutorRole() != null && t.tutorRole().equalsIgnoreCase("Senior Tutor"));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = repo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No user named " + username));
        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority(user.getRole().authority())))
                .disabled(!user.isApproved())
                .build();
    }

    public record RegistrationResult(AppUser user, boolean pendingApproval) {}
}