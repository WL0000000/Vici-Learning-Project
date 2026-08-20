package ca.vicilearning.dashboard.sync;

import ca.vicilearning.dashboard.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrevoSyncEngineServiceTest {

    @Mock StudentRepository studentRepository;
    @Mock BookingRepository bookingRepository;
    @Mock AlertStudentRepository alertStudentRepository;
    @Mock RosterStudentRepository rosterStudentRepository;
    BrevoSyncEngineService syncEngine;

    @BeforeEach
    void setUp() {
        // Constructed manually (not @InjectMocks) so the primitive lapse-threshold arg can be
        // supplied; 21 matches the production default the day-gap assertions below assume.
        syncEngine = new BrevoSyncEngineService(
                studentRepository, bookingRepository, alertStudentRepository, rosterStudentRepository, 21);
    }

    private Student makeStudent(long id, String name, String accountId) {
        Student s = new Student();
        s.setId(id);
        s.setName(name);
        s.setAccountId(accountId);
        return s;
    }

    private Booking makeBooking(String status, LocalDateTime startTime) {
        Booking b = new Booking();
        b.setStatus(status);
        b.setStartTime(startTime);
        return b;
    }

    private RosterStudent makeRoster(String name, String accountId, StudentStatus status) {
        RosterStudent r = new RosterStudent();
        r.setName(name);
        r.setAccountId(accountId);
        r.setStatus(status);
        return r;
    }

    @Test
    void studentWithNoMatchingRosterRecord_isNotFlagged() {
        // no RosterStudent shares this account at all, nothing to compare against
        Student ghost = makeStudent(1L, "Ghost Kid", "Ghost_Account");

        when(studentRepository.findByDeletedAtIsNull()).thenReturn(List.of(ghost));
        when(rosterStudentRepository.findByDeletedAtIsNull()).thenReturn(List.of());

        syncEngine.runTwoWayReconciliationSync();

        verify(alertStudentRepository).deleteById("Ghost Kid");
        verify(alertStudentRepository, never()).save(any());
    }

    @Test
    void matchedActiveRosterButNoBookings_getsFlaggedLapsed() {
        Student sara = makeStudent(1L, "Sara Kim", "Kim_Account");
        RosterStudent roster = makeRoster("Sara Kim", "Kim_Account", StudentStatus.ACTIVE);

        when(studentRepository.findByDeletedAtIsNull()).thenReturn(List.of(sara));
        when(rosterStudentRepository.findByDeletedAtIsNull()).thenReturn(List.of(roster));
        when(bookingRepository.findByStudentId(1L)).thenReturn(List.of());
        when(alertStudentRepository.findById("Sara Kim")).thenReturn(Optional.empty());
        when(alertStudentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        syncEngine.runTwoWayReconciliationSync();

        ArgumentCaptor<AlertStudent> saved = ArgumentCaptor.forClass(AlertStudent.class);
        verify(alertStudentRepository).save(saved.capture());
        assertThat(saved.getValue().isLapsedNow()).isTrue();
        assertThat(saved.getValue().isLapsedStatus()).isFalse(); // roster is Active, so not lapsed per Brevo
        assertThat(saved.getValue().getEmail()).isEqualTo(sara.getEmail());
    }

    @Test
    void matchedActiveRosterWithRecentBooking_isClearedFromAlerts() {
        Student active = makeStudent(2L, "John Park", "Park_Account");
        RosterStudent roster = makeRoster("John Park", "Park_Account", StudentStatus.ACTIVE);
        Booking recent = makeBooking("confirmed", LocalDateTime.now().minusDays(5));

        when(studentRepository.findByDeletedAtIsNull()).thenReturn(List.of(active));
        when(rosterStudentRepository.findByDeletedAtIsNull()).thenReturn(List.of(roster));
        when(bookingRepository.findByStudentId(2L)).thenReturn(List.of(recent));

        syncEngine.runTwoWayReconciliationSync();

        // both sides agree, so the cleanup gate drops the row and never saves
        verify(alertStudentRepository, times(1)).deleteById("John Park");
        verify(alertStudentRepository, never()).save(any());
    }

    @Test
    void cancelledBookingsDontCountTowardActivity() {
        Student s = makeStudent(3L, "Amy Chen", "Chen_Account");
        RosterStudent roster = makeRoster("Amy Chen", "Chen_Account", StudentStatus.ACTIVE);
        Booking cancelled = makeBooking("cancelled", LocalDateTime.now().minusDays(3));

        when(studentRepository.findByDeletedAtIsNull()).thenReturn(List.of(s));
        when(rosterStudentRepository.findByDeletedAtIsNull()).thenReturn(List.of(roster));
        when(bookingRepository.findByStudentId(3L)).thenReturn(List.of(cancelled));
        when(alertStudentRepository.findById("Amy Chen")).thenReturn(Optional.empty());
        when(alertStudentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        syncEngine.runTwoWayReconciliationSync();

        ArgumentCaptor<AlertStudent> saved = ArgumentCaptor.forClass(AlertStudent.class);
        verify(alertStudentRepository).save(saved.capture());
        assertThat(saved.getValue().isLapsedNow()).isTrue();
    }

    @Test
    void droppedRosterStatus_getsRecordedAsMismatchAgainstUpcomingBooking() {
        Student s = makeStudent(4L, "Mike Davis", "Davis_Account");
        RosterStudent roster = makeRoster("Mike Davis", "Davis_Account", StudentStatus.DROPPED);
        Booking upcoming = makeBooking("confirmed", LocalDateTime.now().plusDays(3));

        when(studentRepository.findByDeletedAtIsNull()).thenReturn(List.of(s));
        when(rosterStudentRepository.findByDeletedAtIsNull()).thenReturn(List.of(roster));
        when(bookingRepository.findByStudentId(4L)).thenReturn(List.of(upcoming));
        when(alertStudentRepository.findById("Mike Davis")).thenReturn(Optional.empty());
        when(alertStudentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        syncEngine.runTwoWayReconciliationSync();

        ArgumentCaptor<AlertStudent> saved = ArgumentCaptor.forClass(AlertStudent.class);
        verify(alertStudentRepository).save(saved.capture());
        assertThat(saved.getValue().isLapsedNow()).isFalse();   // has an upcoming booking
        assertThat(saved.getValue().isLapsedStatus()).isTrue(); // but Brevo says Dropped, so mismatch
    }

    @Test
    void studentWithNullName_isSkippedWithoutCrashing() {
        Student broken = makeStudent(5L, null, "X_Account");

        when(studentRepository.findByDeletedAtIsNull()).thenReturn(List.of(broken));
        when(rosterStudentRepository.findByDeletedAtIsNull()).thenReturn(List.of());

        syncEngine.runTwoWayReconciliationSync();

        verify(alertStudentRepository, never()).save(any());
    }

    @Test
    void noStudents_touchesNoBookingsButStillPurgesOrphanedAlerts() {
        // zero active students means every existing alert is orphaned, purge should still run
        AlertStudent orphaned = new AlertStudent();
        orphaned.setName("Leftover");

        when(studentRepository.findByDeletedAtIsNull()).thenReturn(List.of());
        when(rosterStudentRepository.findByDeletedAtIsNull()).thenReturn(List.of());
        when(alertStudentRepository.findAll()).thenReturn(List.of(orphaned));

        syncEngine.runTwoWayReconciliationSync();

        verifyNoInteractions(bookingRepository);
        verify(alertStudentRepository).deleteById("Leftover");
    }

    @Test
    void existingAlertRecord_getsUpdatedNotDuplicated() {
        Student s = makeStudent(6L, "Lisa Wong", "Wong_Account");
        RosterStudent roster = makeRoster("Lisa Wong", "Wong_Account", StudentStatus.ACTIVE);
        AlertStudent existing = new AlertStudent();
        existing.setName("Lisa Wong");
        existing.setAccountId("Wong_Account");
        existing.setLapsedNow(false);

        when(studentRepository.findByDeletedAtIsNull()).thenReturn(List.of(s));
        when(rosterStudentRepository.findByDeletedAtIsNull()).thenReturn(List.of(roster));
        when(bookingRepository.findByStudentId(6L)).thenReturn(List.of());
        when(alertStudentRepository.findById("Lisa Wong")).thenReturn(Optional.of(existing));
        when(alertStudentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        syncEngine.runTwoWayReconciliationSync();

        verify(alertStudentRepository, times(1)).save(any());
    }

    @Test
    void orphanedAlertForNoLongerActiveStudent_getsPurged() {
        // "Old Student" got soft-deleted from SimplyBook after their alert was created, so the
        // reconciliation loop never visits them again, the purge pass is what clears them out
        Student stillActive = makeStudent(9L, "Still Active", "Active_Account");
        RosterStudent roster = makeRoster("Still Active", "Active_Account", StudentStatus.ACTIVE);
        AlertStudent orphaned = new AlertStudent();
        orphaned.setName("Old Student");
        orphaned.setAccountId("Old_Account");

        when(studentRepository.findByDeletedAtIsNull()).thenReturn(List.of(stillActive));
        when(rosterStudentRepository.findByDeletedAtIsNull()).thenReturn(List.of(roster));
        when(bookingRepository.findByStudentId(9L)).thenReturn(List.of());
        when(alertStudentRepository.findAll()).thenReturn(List.of(orphaned));

        syncEngine.runTwoWayReconciliationSync();

        verify(alertStudentRepository).deleteById("Old Student");
    }

    @Test
    void matchesCorrectSiblingByName_acrossAccountIdSuffixVariants() {
        // two siblings on one account, Student side uses "Surname_Account" while the Brevo
        // side just uses "Gray" as the company name. AccountIdNormalizer has to link those, and
        // the right sibling has to get picked by name, not just any roster row on the account.
        Student kaylee = makeStudent(8L, "Kaylee Gray", "Gray_Account");
        RosterStudent hannah = makeRoster("Hannah Gray", "Gray", StudentStatus.ACTIVE);
        RosterStudent kayleeRoster = makeRoster("Kaylee Gray", "Gray", StudentStatus.DROPPED);

        when(studentRepository.findByDeletedAtIsNull()).thenReturn(List.of(kaylee));
        when(rosterStudentRepository.findByDeletedAtIsNull()).thenReturn(List.of(hannah, kayleeRoster));
        when(bookingRepository.findByStudentId(8L)).thenReturn(List.of());

        syncEngine.runTwoWayReconciliationSync();

        // Kaylee has no bookings so lapsedNow is true, and her own roster record is Dropped
        // so both sides agree and this clears rather than flags. picking the wrong sibling
        // (Hannah, Active) would have made this a mismatch instead.
        verify(alertStudentRepository).deleteById("Kaylee Gray");
        verify(alertStudentRepository, never()).save(any());
    }
}
