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
                .requestMatchers("/sync/**", "/comms/**").hasAnyRole("ADMIN", "STAFF")
                // Everything under /students — including POST /students/{id}/status (the enrolment
                // ACTIVE/PAUSED toggle) — is ADMIN/STAFF; tutors are redirected to their own portal.
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