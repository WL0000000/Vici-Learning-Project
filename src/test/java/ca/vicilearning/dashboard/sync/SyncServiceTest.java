package ca.vicilearning.dashboard.sync;

import ca.vicilearning.dashboard.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock SimplybookClient   client;
    @Mock ClientAdapter      clientAdapter;
    @Mock PerformerAdapter   performerAdapter;
    @Mock ServiceAdapter     serviceAdapter;
    @Mock BookingAdapter     bookingAdapter;
    @Mock StudentRepository  studentRepo;
    @Mock TutorRepository    tutorRepo;
    @Mock ServiceRepository  serviceRepo;
    @Mock BookingRepository  bookingRepo;
    @Mock SyncLogRepository  syncLogRepo;
    @Mock PlatformTransactionManager txManager;

    @InjectMocks SyncService syncService;

    @BeforeEach
    void txReturnsRealStatus() {
        // Make each step's TransactionTemplate run its action; commit/rollback are no-ops
        // on the mock, so step bodies execute exactly as in production minus real DB tx.
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void failingStep_doesNotPreventOtherSteps_andRecordsFailure() {
        // The tutors step blows up on the upstream call
        when(client.getPerformerList()).thenThrow(new SimplybookApiException(-1, "boom"));
        when(serviceAdapter.toServices(any())).thenReturn(List.of(new Service()));
        when(clientAdapter.toStudents(any())).thenReturn(List.of(new Student()));
        when(studentRepo.findAll()).thenReturn(List.of());
        when(tutorRepo.findAll()).thenReturn(List.of());
        when(serviceRepo.findAll()).thenReturn(List.of());
        when(bookingAdapter.toBookings(any(), any(), any(), any())).thenReturn(List.of());
        when(bookingRepo.findByStartTimeBetween(any(), any())).thenReturn(List.of());

        SyncLog result = syncService.sync();

        // atLeastOnce: each step now calls saveAll twice (upsert + soft-delete reconcile).
        verify(serviceRepo, atLeastOnce()).saveAll(any());
        verify(studentRepo, atLeastOnce()).saveAll(any());
        verify(bookingRepo, atLeastOnce()).saveAll(any());

        // The run is marked failed and the failing step is named in the error message.
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("tutors");
    }

    @Test
    void allStepsSucceed_marksRunSuccessfulWithNoError() {
        when(performerAdapter.toTutors(any())).thenReturn(List.of(new Tutor()));
        when(serviceAdapter.toServices(any())).thenReturn(List.of(new Service()));
        when(clientAdapter.toStudents(any())).thenReturn(List.of(new Student()));
        when(studentRepo.findAll()).thenReturn(List.of());
        when(tutorRepo.findAll()).thenReturn(List.of());
        when(serviceRepo.findAll()).thenReturn(List.of());
        when(bookingAdapter.toBookings(any(), any(), any(), any())).thenReturn(List.of());
        when(bookingRepo.findByStartTimeBetween(any(), any())).thenReturn(List.of());

        SyncLog result = syncService.sync();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void secondSyncWhileOneIsRunning_isSkipped() {
        AtomicReference<SyncLog> reentrant = new AtomicReference<>();

        // While the outer sync is mid-flight (inside the first step), fire a second
        // sync() on the same instance. The guard must make it a no-op returning null.
        when(client.getPerformerList()).thenAnswer(inv -> {
            reentrant.set(syncService.sync());
            return null;
        });
        when(performerAdapter.toTutors(any())).thenReturn(List.of(new Tutor()));
        when(serviceAdapter.toServices(any())).thenReturn(List.of(new Service()));
        when(clientAdapter.toStudents(any())).thenReturn(List.of(new Student()));
        when(studentRepo.findAll()).thenReturn(List.of());
        when(tutorRepo.findAll()).thenReturn(List.of());
        when(serviceRepo.findAll()).thenReturn(List.of());
        when(bookingAdapter.toBookings(any(), any(), any(), any())).thenReturn(List.of());
        when(bookingRepo.findByStartTimeBetween(any(), any())).thenReturn(List.of());

        SyncLog outer = syncService.sync();

        // Outer run completed normally; the overlapping call was skipped (null).
        assertThat(outer).isNotNull();
        assertThat(outer.isSuccess()).isTrue();
        assertThat(reentrant.get()).isNull();

        // The skipped call never started a second run, so only the outer SyncLog was persisted.
        verify(client, times(1)).getPerformerList();
    }

    @Test
    void rowMissingUpstream_isSoftDeleted_whilePresentRowsAreUntouched() {
        Tutor live  = tutorWithId(1L);   // still returned by SimplyBook.me
        Tutor stale = tutorWithId(2L);   // in our DB but no longer upstream

        when(performerAdapter.toTutors(any())).thenReturn(List.of(live));
        // findAll reflects the post-upsert state: both the live and the stale row.
        when(tutorRepo.findAll()).thenReturn(List.of(live, stale));
        // Remaining steps are no-ops.
        when(serviceAdapter.toServices(any())).thenReturn(List.of());
        when(serviceRepo.findAll()).thenReturn(List.of());
        when(clientAdapter.toStudents(any())).thenReturn(List.of());
        when(studentRepo.findAll()).thenReturn(List.of());
        when(bookingAdapter.toBookings(any(), any(), any(), any())).thenReturn(List.of());
        when(bookingRepo.findByStartTimeBetween(any(), any())).thenReturn(List.of());

        SyncLog result = syncService.sync();

        assertThat(stale.getDeletedAt()).isNotNull();   // gone upstream → soft-deleted
        assertThat(live.getDeletedAt()).isNull();       // still present → left alone
        assertThat(result.getTutorsRemoved()).isEqualTo(1);
        assertThat(result.isSuccess()).isTrue();
    }

    private static Tutor tutorWithId(long id) {
        Tutor t = new Tutor();
        t.setId(id);
        return t;
    }
}
