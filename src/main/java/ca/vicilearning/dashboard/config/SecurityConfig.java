package ca.vicilearning.dashboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final RoleBasedLoginSuccessHandler loginSuccessHandler;

    public SecurityConfig(RoleBasedLoginSuccessHandler loginSuccessHandler) {
        this.loginSuccessHandler = loginSuccessHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register", "/css/**", "/js/**").permitAll()
                .requestMatchers("/admin/users/**").hasRole("ADMIN")
                // Editing a student's ACTIVE/PAUSED status is admin-only — narrower than the rest
                // of the students page (ADMIN/STAFF, matched below), so it must come first.
                .requestMatchers(HttpMethod.POST, "/students/*/status").hasRole("ADMIN")
                .requestMatchers("/sync/**", "/comms/**").hasAnyRole("ADMIN", "STAFF")
                .requestMatchers("/", "/students/**", "/api/notion/**", "/associations/**")
                    .hasAnyRole("ADMIN", "STAFF")
                .requestMatchers("/tutor-portal/**").hasAnyRole("ADMIN", "STAFF", "TUTOR")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(loginSuccessHandler)
                .permitAll())
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}