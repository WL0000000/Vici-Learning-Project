package ca.vicilearning.dashboard.sync;

import ca.vicilearning.dashboard.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks SyncService syncService;

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

        SyncLog result = syncService.sync();

        
        verify(serviceRepo).saveAll(any());
        verify(studentRepo).saveAll(any());
        verify(bookingRepo).saveAll(any());

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

        SyncLog outer = syncService.sync();

        // Outer run completed normally; the overlapping call was skipped (null).
        assertThat(outer).isNotNull();
        assertThat(outer.isSuccess()).isTrue();
        assertThat(reentrant.get()).isNull();

        // The skipped call never started a second run, so only the outer SyncLog was persisted.
        verify(client, times(1)).getPerformerList();
    }
}
