package ca.vicilearning.dashboard.sync;

import ca.vicilearning.dashboard.comms.BrevoCommunicationService;
import ca.vicilearning.dashboard.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrevoSyncEngineServiceTest {

    @Mock StudentRepository studentRepository;
    @Mock BookingRepository bookingRepository;
    @Mock AlertStudentRepository alertStudentRepository;
    @Mock BrevoCommunicationService communicationService;
    @InjectMocks BrevoSyncEngineService syncEngine;

    // Helper to generate consistent mock students
    private Student makeStudent(long id, String name, String accountId) {
        Student s = new Student();
        s.setId(id);
        s.setName(name);
        s.setAccountId(accountId);
        return s;
    }

    // Helper to generate consistent mock bookings
    private Booking makeBooking(String status, LocalDateTime startTime) {
        Booking b = new Booking();
        b.setStatus(status);
        b.setStartTime(startTime);
        return b;
    }

    @Test
    void student_with_no_bookings_gets_flagged_as_lapsed() {
        Student sara = makeStudent(1L, "Sara Kim", "VICI-0001");

        when(studentRepository.findByDeletedAtIsNull()).thenReturn(List.of(sara));
        // System returns an empty nested map structure
        when(communicationService.fetchStudentStatusMap()).thenReturn(Map.of());
        when(bookingRepository.findByStudentId(1L)).thenReturn(List.of());
        when(alertStudentRepository.findById("Sara Kim")).thenReturn(Optional.empty());
        when(alertStudentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        syncEngine.runTwoWayReconciliationSync();

        ArgumentCaptor<AlertStudent> saved = ArgumentCaptor.forClass(AlertStudent.class);
        verify(alertStudentRepository).save(saved.capture());
        assertThat(saved.getValue().isLapsedNow()).isTrue();
        assertThat(saved.getValue().isLapsedStatus()).isFalse(); // Defaults to Active
    }

    @Test
    void student_with_recent_confirmed_booking_is_cleared_from_alerts() {
        Student active = makeStudent(2L, "John Park", "VICI-0002");
        Booking recent = makeBooking("confirmed", LocalDateTime.now().minusDays(5));

        when(studentRepository.findByDeletedAtIsNull()).thenReturn(List.of(active));
        // Brevo defaults to Active, Local rules calculate Active (Not Lapsed) -> Systems In Sync!
        when(communicationService.fetchStudentStatusMap()).thenReturn(Map.of());
        when(bookingRepository.findByStudentId(2L)).thenReturn(List.of(recent));

        syncEngine.runTwoWayReconciliationSync();

        // VERIFICATION UPDATE: Since the systems are in sync, the proactive cleanup engine 
        // drops the row from the alert database and bypasses the save method entirely!
        verify(alertStudentRepository, times(1)).deleteById("John Park");
        verify(alertStudentRepository, never()).save(any());
    }

    @Test
    void cancelled_bookings_dont_count_toward_activity() {
        Student s = makeStudent(3L, "Amy Chen", "VICI-0003");
        Booking cancelled = makeBooking("cancelled", LocalDateTime.now().minusDays(3));

        when(studentRepository.findByDeletedAtIsNull()).thenReturn(List.of(s));
        when(communicationService.fetchStudentStatusMap()).thenReturn(Map.of());
        when(bookingRepository.findByStudentId(3L)).thenReturn(List.of(cancelled));
        when(alertStudentRepository.findById("Amy Chen")).thenReturn(Optional.empty());
        when(alertStudentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        syncEngine.runTwoWayReconciliationSync();

        ArgumentCaptor<AlertStudent> saved = ArgumentCaptor.forClass(AlertStudent.class);
        verify(alertStudentRepository).save(saved.capture());
        assertThat(saved.getValue().isLapsedNow()).isTrue();
    }

    @Test
    void brevo_lapsed_status_gets_recorded_correctly() {
        Student s = makeStudent(4L, "Mike Davis", "VICI-0004");
        Booking upcoming = makeBooking("confirmed", LocalDateTime.now().plusDays(3));

        // UPGRADED LINE 110: Stub uses account identifier to secure the sibling nested map
        Map<String, Map<String, String>> mockNestedMap = Map.of(
            "VICI-0004", Map.of("mike davis", "Lapsed")
        );

        when(studentRepository.findByDeletedAtIsNull()).thenReturn(List.of(s));
        when(communicationService.fetchStudentStatusMap()).thenReturn(mockNestedMap);
        when(bookingRepository.findByStudentId(4L)).thenReturn(List.of(upcoming));
        when(alertStudentRepository.findById("Mike Davis")).thenReturn(Optional.empty());
        when(alertStudentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        syncEngine.runTwoWayReconciliationSync();

        ArgumentCaptor<AlertStudent> saved = ArgumentCaptor.forClass(AlertStudent.class);
        verify(alertStudentRepository).save(saved.capture());
        assertThat(saved.getValue().isLapsedNow()).isFalse(); // Local bookings say Active
        assertThat(saved.getValue().isLapsedStatus()).isTrue();  // Brevo say Lapsed -> Mismatch detected!
    }

    @Test
    void student_with_null_name_gets_skipped_without_crashing() {
        Student broken = makeStudent(5L, null, "VICI-0005");

        when(studentRepository.findByDeletedAtIsNull()).thenReturn(List.of(broken));
        when(communicationService.fetchStudentStatusMap()).thenReturn(Map.of());

        syncEngine.runTwoWayReconciliationSync();

        verify(alertStudentRepository, never()).save(any());
    }

    @Test
    void no_students_exits_early_without_touching_anything() {
        when(studentRepository.findByDeletedAtIsNull()).thenReturn(List.of());
        when(communicationService.fetchStudentStatusMap()).thenReturn(Map.of());

        syncEngine.runTwoWayReconciliationSync();

        verifyNoInteractions(bookingRepository);
        verifyNoInteractions(alertStudentRepository);
    }

    @Test
    void existing_alert_record_gets_updated_not_duplicated() {
        Student s = makeStudent(6L, "Lisa Wong", "VICI-0006");
        AlertStudent existing = new AlertStudent();
        existing.setName("Lisa Wong");
        existing.setAccountId("VICI-0006");
        existing.setLapsedNow(false);

        when(studentRepository.findByDeletedAtIsNull()).thenReturn(List.of(s));
        when(communicationService.fetchStudentStatusMap()).thenReturn(Map.of());
        when(bookingRepository.findByStudentId(6L)).thenReturn(List.of());
        when(alertStudentRepository.findById("Lisa Wong")).thenReturn(Optional.of(existing));
        when(alertStudentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        syncEngine.runTwoWayReconciliationSync();

        verify(alertStudentRepository, times(1)).save(any());
    }
}