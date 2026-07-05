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
    @Mock SimplybookRestClient restClient;
    @Mock SimplybookProperties props;
    @Mock ClientAdapter      clientAdapter;
    @Mock PerformerAdapter   performerAdapter;
    @Mock ServiceAdapter     serviceAdapter;
    @Mock BookingAdapter     bookingAdapter;
    @Mock InvoiceAdapter     invoiceAdapter;
    @Mock MembershipAdapter  membershipAdapter;
    @Mock StudentRepository  studentRepo;
    @Mock TutorRepository    tutorRepo;
    @Mock ServiceRepository  serviceRepo;
    @Mock BookingRepository  bookingRepo;
    @Mock InvoiceRepository  invoiceRepo;
    @Mock MembershipRepository membershipRepo;
    @Mock SyncLogRepository  syncLogRepo;
    @Mock PlatformTransactionManager txManager;

    @InjectMocks SyncService syncService;

    @BeforeEach
    void txReturnsRealStatus() {
        // Make each step's TransactionTemplate run its action; commit/rollback are no-ops
        // on the mock, so step bodies execute exactly as in production minus real DB tx.
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    // When REST is configured, the invoice + membership steps run alongside the Account_ID
    // step. Tests focused on Account_ID stub those to no-ops so the unrelated REST steps don't
    // NPE on the (mocked) repositories.
    private void stubEmptyInvoiceAndMembershipSteps() {
        when(invoiceAdapter.toInvoices(any(), any())).thenReturn(List.of());
        when(membershipAdapter.toMemberships(any(), any())).thenReturn(List.of());
        when(invoiceRepo.findAll()).thenReturn(List.of());
        when(membershipRepo.findAll()).thenReturn(List.of());
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

    @Test
    void accountIdStep_populatesStudentFromRestV2_whenConfigured() {
        // All JSON-RPC steps are no-ops so we isolate the REST v2 Account_ID step.
        when(performerAdapter.toTutors(any())).thenReturn(List.of());
        when(serviceAdapter.toServices(any())).thenReturn(List.of());
        when(clientAdapter.toStudents(any())).thenReturn(List.of());
        when(studentRepo.findAll()).thenReturn(List.of());
        when(tutorRepo.findAll()).thenReturn(List.of());
        when(serviceRepo.findAll()).thenReturn(List.of());
        when(bookingAdapter.toBookings(any(), any(), any(), any())).thenReturn(List.of());
        when(bookingRepo.findByStartTimeBetween(any(), any())).thenReturn(List.of());

        Student student = new Student();
        student.setId(5L);
        when(props.restConfigured()).thenReturn(true);
        when(props.accountIdFieldTitle()).thenReturn("Account_ID");
        when(studentRepo.findByDeletedAtIsNullAndAccountIdIsNull()).thenReturn(List.of(student));
        when(clientAdapter.extractAccountId(any(), eq("Account_ID"))).thenReturn("VICI-001");
        stubEmptyInvoiceAndMembershipSteps();

        SyncLog result = syncService.sync();

        assertThat(student.getAccountId()).isEqualTo("VICI-001");
        assertThat(result.getAccountIdsLinked()).isEqualTo(1);
        assertThat(result.isSuccess()).isTrue();
        verify(studentRepo).save(student);
    }

    @Test
    void accountIdStep_makesNoRestCalls_whenEveryStudentAlreadyLinked() {
        when(performerAdapter.toTutors(any())).thenReturn(List.of());
        when(serviceAdapter.toServices(any())).thenReturn(List.of());
        when(clientAdapter.toStudents(any())).thenReturn(List.of());
        when(studentRepo.findAll()).thenReturn(List.of());
        when(tutorRepo.findAll()).thenReturn(List.of());
        when(serviceRepo.findAll()).thenReturn(List.of());
        when(bookingAdapter.toBookings(any(), any(), any(), any())).thenReturn(List.of());
        when(bookingRepo.findByStartTimeBetween(any(), any())).thenReturn(List.of());

        // REST is configured, but the backlog query returns nothing: at steady state every
        // active student already has an Account_ID, so the step must fetch client fields zero
        // times (the invoice/membership steps still run, hence not verifyNoInteractions).
        when(props.restConfigured()).thenReturn(true);
        when(studentRepo.findByDeletedAtIsNullAndAccountIdIsNull()).thenReturn(List.of());
        stubEmptyInvoiceAndMembershipSteps();

        SyncLog result = syncService.sync();

        assertThat(result.getAccountIdsLinked()).isEqualTo(0);
        assertThat(result.isSuccess()).isTrue();
        verify(restClient, never()).getClientFieldValues(anyLong());
    }

    @Test
    void syncStudents_preservesExistingAccountId_soUpsertDoesNotBlankIt() {
        // A student already linked in our DB; the adapter re-delivers it with accountId == null
        // (JSON-RPC can't read custom fields). The upsert must keep the known Account_ID.
        Student existing = new Student();
        existing.setId(7L);
        existing.setAccountId("VICI-007");

        Student fromApi = new Student();  // same id, freshly adapted, no accountId
        fromApi.setId(7L);

        when(performerAdapter.toTutors(any())).thenReturn(List.of());
        when(serviceAdapter.toServices(any())).thenReturn(List.of());
        when(clientAdapter.toStudents(any())).thenReturn(List.of(fromApi));
        when(studentRepo.findAll()).thenReturn(List.of(existing));
        when(tutorRepo.findAll()).thenReturn(List.of());
        when(serviceRepo.findAll()).thenReturn(List.of());
        when(bookingAdapter.toBookings(any(), any(), any(), any())).thenReturn(List.of());
        when(bookingRepo.findByStartTimeBetween(any(), any())).thenReturn(List.of());

        syncService.sync();

        // The carry-over ran before saveAll, so the upserted student keeps its Account_ID.
        assertThat(fromApi.getAccountId()).isEqualTo("VICI-007");
    }

    @Test
    void invoiceAndMembershipSteps_upsertAndReconcile_whenConfigured() {
        // JSON-RPC + Account_ID steps are no-ops so we isolate the REST v2 invoice/membership steps.
        when(performerAdapter.toTutors(any())).thenReturn(List.of());
        when(serviceAdapter.toServices(any())).thenReturn(List.of());
        when(clientAdapter.toStudents(any())).thenReturn(List.of());
        when(studentRepo.findAll()).thenReturn(List.of());
        when(tutorRepo.findAll()).thenReturn(List.of());
        when(serviceRepo.findAll()).thenReturn(List.of());
        when(bookingAdapter.toBookings(any(), any(), any(), any())).thenReturn(List.of());
        when(bookingRepo.findByStartTimeBetween(any(), any())).thenReturn(List.of());
        when(props.restConfigured()).thenReturn(true);
        when(studentRepo.findByDeletedAtIsNullAndAccountIdIsNull()).thenReturn(List.of());

        Invoice liveInvoice = new Invoice();
        liveInvoice.setId(1L);
        Invoice staleInvoice = new Invoice();  // in our DB but no longer upstream
        staleInvoice.setId(2L);
        when(invoiceAdapter.toInvoices(any(), any())).thenReturn(List.of(liveInvoice));
        when(invoiceRepo.findAll()).thenReturn(List.of(liveInvoice, staleInvoice));

        Membership membership = new Membership();
        membership.setId(10L);
        when(membershipAdapter.toMemberships(any(), any())).thenReturn(List.of(membership));
        when(membershipRepo.findAll()).thenReturn(List.of(membership));

        SyncLog result = syncService.sync();

        assertThat(result.getInvoicesUpserted()).isEqualTo(1);
        assertThat(result.getInvoicesRemoved()).isEqualTo(1);
        assertThat(staleInvoice.getDeletedAt()).isNotNull();   // gone upstream → soft-deleted
        assertThat(liveInvoice.getDeletedAt()).isNull();       // still present → left alone
        assertThat(result.getMembershipsUpserted()).isEqualTo(1);
        assertThat(result.getMembershipsRemoved()).isEqualTo(0);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void invoiceAndMembershipSteps_skippedCleanly_whenRestNotConfigured() {
        when(performerAdapter.toTutors(any())).thenReturn(List.of());
        when(serviceAdapter.toServices(any())).thenReturn(List.of());
        when(clientAdapter.toStudents(any())).thenReturn(List.of());
        when(studentRepo.findAll()).thenReturn(List.of());
        when(tutorRepo.findAll()).thenReturn(List.of());
        when(serviceRepo.findAll()).thenReturn(List.of());
        when(bookingAdapter.toBookings(any(), any(), any(), any())).thenReturn(List.of());
        when(bookingRepo.findByStartTimeBetween(any(), any())).thenReturn(List.of());

        // restConfigured() defaults false → both REST v2 steps must no-op, not fail, and never
        // touch their adapters or repositories.
        SyncLog result = syncService.sync();

        assertThat(result.getInvoicesUpserted()).isEqualTo(0);
        assertThat(result.getMembershipsUpserted()).isEqualTo(0);
        assertThat(result.isSuccess()).isTrue();
        verifyNoInteractions(invoiceAdapter, membershipAdapter, invoiceRepo, membershipRepo);
    }

    @Test
    void accountIdStep_isSkippedCleanly_whenRestNotConfigured() {
        when(performerAdapter.toTutors(any())).thenReturn(List.of());
        when(serviceAdapter.toServices(any())).thenReturn(List.of());
        when(clientAdapter.toStudents(any())).thenReturn(List.of());
        when(studentRepo.findAll()).thenReturn(List.of());
        when(tutorRepo.findAll()).thenReturn(List.of());
        when(serviceRepo.findAll()).thenReturn(List.of());
        when(bookingAdapter.toBookings(any(), any(), any(), any())).thenReturn(List.of());
        when(bookingRepo.findByStartTimeBetween(any(), any())).thenReturn(List.of());

        // restConfigured() defaults to false on the mock → step must no-op, not fail,
        // and must never call the REST client.
        SyncLog result = syncService.sync();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAccountIdsLinked()).isEqualTo(0);
        verifyNoInteractions(restClient);
    }
}
