package ca.vicilearning.dashboard.auth;

import ca.vicilearning.dashboard.notion.NotionService;
import ca.vicilearning.dashboard.notion.NotionTutor;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    @Mock AppUserRepository repo;
    @Mock NotionService notionService;

    // A real BCrypt encoder so we exercise actual hashing, not a stub.
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private AppUserService service;

    @BeforeEach
    void setUp() {
        service = new AppUserService(repo, encoder, notionService);
    }
    
    // helper for building a NotionTutor row with just the fields these tests care about;
    // the record has 28 fields total (id..url) and most are irrelevant here
    private NotionTutor tutorWithEmailAndRole(String email, String tutorRole) {
        return new NotionTutor(
                null, null, null, email, null, null, null,      // id, name, tutorId, email, phone, city, postalCode
                null, null, null, null, null, null, null,       // streetAddress, oneSentenceBio, bookingLink, atHomeTutoring, centreTutoring, virtualTutoring, languages
                null, null, null, null, null, null, null,       // subjects, startDate, endDate, status, atHomeRate, virtualCentreRate, supportPayRate
                null, null, null, tutorRole, null, null, null   // person, viciRole, tutorProfile, tutorRole, assignedAdmin, adminNotes, url
        );
    }

    @Test
    void registerSelfService_regularEmail_landsAsTutorPendingApproval() {
        when(notionService.getTutorRows()).thenReturn(List.of());
        when(repo.existsByUsername("alice")).thenReturn(false);
        when(repo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUserService.RegistrationResult result = service.registerSelfService("alice", "supersecret");
        AppUser user = result.user();

        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getRole()).isEqualTo(Role.TUTOR);
        assertThat(user.isApproved()).isFalse();
        assertThat(result.pendingApproval()).isTrue();
        assertThat(user.getPassword()).isNotEqualTo("supersecret"); // never stored plaintext
        assertThat(encoder.matches("supersecret", user.getPassword())).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    void registerSelfService_seniorTutorEmail_autoElevatesToStaff_andApproves() {
        when(notionService.getTutorRows())
                .thenReturn(List.of(tutorWithEmailAndRole("senior@vicilearning.com", "Senior Tutor")));
        when(repo.existsByUsername("senior@vicilearning.com")).thenReturn(false);
        when(repo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUserService.RegistrationResult result =
                service.registerSelfService("senior@vicilearning.com", "supersecret");

        assertThat(result.user().getRole()).isEqualTo(Role.STAFF);
        assertThat(result.user().isApproved()).isTrue();
        assertThat(result.pendingApproval()).isFalse();
    }

    @Test
    void registerSelfService_regularTutorInNotion_staysTutorPending() {
        // Notion has this person, but they're not a Senior Tutor — shouldn't get elevated.
        when(notionService.getTutorRows())
                .thenReturn(List.of(tutorWithEmailAndRole("regular@vicilearning.com", "Tutor")));
        when(repo.existsByUsername("regular@vicilearning.com")).thenReturn(false);
        when(repo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUserService.RegistrationResult result =
                service.registerSelfService("regular@vicilearning.com", "supersecret");

        assertThat(result.user().getRole()).isEqualTo(Role.TUTOR);
        assertThat(result.pendingApproval()).isTrue();
    }

    @Test
    void registerSelfService_notionUnreachable_fallsBackToTutorPending_withoutThrowing() {
        // Notion being down shouldn't block registration, just skip the elevation check.
        when(notionService.getTutorRows()).thenThrow(new RuntimeException("Notion API down"));
        when(repo.existsByUsername("alice")).thenReturn(false);
        when(repo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUserService.RegistrationResult result = service.registerSelfService("alice", "supersecret");

        assertThat(result.user().getRole()).isEqualTo(Role.TUTOR);
        assertThat(result.pendingApproval()).isTrue();
    }

    @Test
    void registerSelfService_trimsUsername() {
        when(notionService.getTutorRows()).thenReturn(List.of());
        when(repo.existsByUsername("bob")).thenReturn(false);
        when(repo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUserService.RegistrationResult result = service.registerSelfService("  bob  ", "supersecret");

        assertThat(result.user().getUsername()).isEqualTo("bob");
    }

    @Test
    void registerSelfService_rejectsDuplicateUsername() {
        when(notionService.getTutorRows()).thenReturn(List.of());
        when(repo.existsByUsername("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.registerSelfService("taken", "supersecret"))
                .isInstanceOf(DuplicateUsernameException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void registerSelfService_rejectsBlankUsername() {
        assertThatThrownBy(() -> service.registerSelfService("   ", "supersecret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username");
        verify(repo, never()).save(any());
    }

    @Test
    void registerSelfService_rejectsShortPassword() {
        when(notionService.getTutorRows()).thenReturn(List.of());

        assertThatThrownBy(() -> service.registerSelfService("alice", "short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password");
        verify(repo, never()).save(any());
    }

    @Test
    void registerAsAdmin_isAlwaysApproved_regardlessOfRole() {
        when(repo.existsByUsername("root")).thenReturn(false);
        when(repo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser created = service.registerAsAdmin("root", "supersecret", Role.ADMIN);

        assertThat(created.getRole()).isEqualTo(Role.ADMIN);
        assertThat(created.isApproved()).isTrue();
        verifyNoInteractions(notionService); // admin-created accounts skip the Notion lookup entirely
    }

    @Test
    void loadUserByUsername_mapsRoleToAuthority() {
        AppUser stored = new AppUser();
        stored.setUsername("alice");
        stored.setPassword(encoder.encode("supersecret"));
        stored.setRole(Role.ADMIN);
        stored.setApproved(true);
        when(repo.findByUsername("alice")).thenReturn(Optional.of(stored));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void loadUserByUsername_unapprovedAccount_isDisabled() {
        // this is the whole point of the approval workflow: Spring Security refuses login
        // automatically for anyone who isn't approved yet, no extra code needed elsewhere
        AppUser stored = new AppUser();
        stored.setUsername("pending-tutor");
        stored.setPassword(encoder.encode("supersecret"));
        stored.setRole(Role.TUTOR);
        stored.setApproved(false);
        when(repo.findByUsername("pending-tutor")).thenReturn(Optional.of(stored));

        UserDetails details = service.loadUserByUsername("pending-tutor");

        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    void loadUserByUsername_throwsWhenMissing() {
        when(repo.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}