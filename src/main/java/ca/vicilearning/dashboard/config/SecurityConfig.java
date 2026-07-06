package ca.vicilearning.dashboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // /register and /login are public so unauthenticated visitors can sign up and log in.
            // Admin-only areas: the sync console and the Brevo automations/communications queue
            // both expose or act on sensitive data (billing links, family contact info), so they
            // require ADMIN. Tutors keep access to the students and overview pages, but the
            // sensitive columns there are hidden in the view (see students.html).
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register", "/css/**", "/js/**").permitAll()
                .requestMatchers("/sync/**", "/comms/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .permitAll())
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll());

        return http.build();
    }

    /**
     * BCrypt hashing for stored passwords. Spring Security wires this together with the
     * {@code AppUserService} (a {@code UserDetailsService}) into the DAO auth provider, so
     * login checks the submitted password against the BCrypt hash in the database.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
