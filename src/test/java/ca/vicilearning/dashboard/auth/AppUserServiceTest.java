package ca.vicilearning.dashboard.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    @Mock AppUserRepository repo;

    // A real BCrypt encoder so we exercise actual hashing, not a stub.
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private AppUserService service;

    @BeforeEach
    void setUp() {
        service = new AppUserService(repo, encoder);
    }

    @Test
    void register_hashesPassword_assignsTutorRole_andSaves() {
        when(repo.existsByUsername("alice")).thenReturn(false);
        when(repo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser created = service.register("alice", "supersecret");

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(repo).save(saved.capture());
        AppUser user = saved.getValue();

        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getRole()).isEqualTo(Role.TUTOR);          // self-registration default
        assertThat(user.getPassword()).isNotEqualTo("supersecret"); // never stored plaintext
        assertThat(encoder.matches("supersecret", user.getPassword())).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(created).isSameAs(user);
    }

    @Test
    void register_trimsUsername() {
        when(repo.existsByUsername("bob")).thenReturn(false);
        when(repo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser created = service.register("  bob  ", "supersecret");

        assertThat(created.getUsername()).isEqualTo("bob");
    }

    @Test
    void register_rejectsDuplicateUsername() {
        when(repo.existsByUsername("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.register("taken", "supersecret"))
                .isInstanceOf(DuplicateUsernameException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void register_rejectsBlankUsername() {
        assertThatThrownBy(() -> service.register("   ", "supersecret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username");
        verifyNoInteractions(repo);
    }

    @Test
    void register_rejectsShortPassword() {
        assertThatThrownBy(() -> service.register("alice", "short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password");
        verify(repo, never()).save(any());
    }

    @Test
    void register_withExplicitRole_isHonoured() {
        when(repo.existsByUsername("root")).thenReturn(false);
        when(repo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser created = service.register("root", "supersecret", Role.ADMIN);

        assertThat(created.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void loadUserByUsername_mapsRoleToAuthority() {
        AppUser stored = new AppUser();
        stored.setUsername("alice");
        stored.setPassword(encoder.encode("supersecret"));
        stored.setRole(Role.ADMIN);
        when(repo.findByUsername("alice")).thenReturn(Optional.of(stored));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void loadUserByUsername_throwsWhenMissing() {
        when(repo.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
