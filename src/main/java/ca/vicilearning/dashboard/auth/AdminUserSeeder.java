package ca.vicilearning.dashboard.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Ensures an ADMIN login always exists so graders/staff can sign in even on a fresh database.
 * Reads the same {@code ADMIN_USERNAME}/{@code ADMIN_PASSWORD} used elsewhere and creates the
 * admin only if it isn't already present, so it never overwrites a changed password on restart.
 */
@Component
public class AdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final AppUserService users;
    private final AppUserRepository repo;
    private final String adminUsername;
    private final String adminPassword;

    public AdminUserSeeder(AppUserService users,
                           AppUserRepository repo,
                           @Value("${ADMIN_USERNAME:Admin}") String adminUsername,
                           @Value("${ADMIN_PASSWORD:ViciLearning2026}") String adminPassword) {
        this.users = users;
        this.repo = repo;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repo.existsByUsername(adminUsername)) {
            return;
        }
        users.register(adminUsername, adminPassword, Role.ADMIN);
        log.info("Seeded ADMIN login '{}' (change ADMIN_PASSWORD before production)", adminUsername);
    }
}
