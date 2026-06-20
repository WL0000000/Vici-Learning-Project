package ca.vicilearning.dashboard.sync;

import ca.vicilearning.dashboard.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private static final int BOOKING_LOOKBACK_DAYS  = 90;
    private static final int BOOKING_LOOKAHEAD_DAYS = 30;

    private final SimplybookClient        client;
    private final ClientAdapter           clientAdapter;
    private final PerformerAdapter        performerAdapter;
    private final ServiceAdapter          serviceAdapter;
    private final BookingAdapter          bookingAdapter;
    private final StudentRepository       studentRepo;
    private final TutorRepository         tutorRepo;
    private final ServiceRepository       serviceRepo;
    private final BookingRepository       bookingRepo;
    private final SyncLogRepository       syncLogRepo;

    // Ensures only one sync runs at a time: the hourly scheduler and a manual
    // "Sync Now" click can otherwise race on the same tables.
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

    // Runs each step in its own transaction. We use TransactionTemplate rather than
    // @Transactional because the steps are invoked via self-invocation from doSync(),
    // which Spring's proxy-based @Transactional does not intercept.
    private final TransactionTemplate transactionTemplate;

    public SyncService(SimplybookClient client,
                       ClientAdapter clientAdapter,
                       PerformerAdapter performerAdapter,
                       ServiceAdapter serviceAdapter,
                       BookingAdapter bookingAdapter,
                       StudentRepository studentRepo,
                       TutorRepository tutorRepo,
                       ServiceRepository serviceRepo,
                       BookingRepository bookingRepo,
                       SyncLogRepository syncLogRepo,
                       PlatformTransactionManager transactionManager) {
        this.client          = client;
        this.clientAdapter   = clientAdapter;
        this.performerAdapter = performerAdapter;
        this.serviceAdapter  = serviceAdapter;
        this.bookingAdapter  = bookingAdapter;
        this.studentRepo     = studentRepo;
        this.tutorRepo       = tutorRepo;
        this.serviceRepo     = serviceRepo;
        this.bookingRepo     = bookingRepo;
        this.syncLogRepo     = syncLogRepo;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Runs a full sync. If a sync is already running, this call is skipped and
     * returns {@code null} rather than racing the in-flight run.
     */
    public SyncLog sync() {
        if (!syncInProgress.compareAndSet(false, true)) {
            log.info("Sync already in progress; skipping this run");
            return null;
        }
        try {
            return doSync();
        } finally {
            syncInProgress.set(false);
        }
    }

    private SyncLog doSync() {
        SyncLog entry = new SyncLog();
        entry.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
        entry.setSuccess(false);
        syncLogRepo.save(entry);

        log.info("Starting SimplyBook.me sync");

        // Each step runs independently: a failure in one upstream resource (e.g. a
        // flaky getUnitList call) must not prevent the others from syncing. We collect
        // per-step failures and only mark the run successful if every step succeeded.
        List<String> failures = new ArrayList<>();
        runStep("tutors",   () -> syncTutors(entry),   failures);
        runStep("services", () -> syncServices(entry), failures);
        runStep("students", () -> syncStudents(entry), failures);
        runStep("bookings", () -> syncBookings(entry), failures);

        entry.setSuccess(failures.isEmpty());
        if (!failures.isEmpty()) {
            entry.setErrorMessage(String.join("; ", failures));
        }
        entry.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
        syncLogRepo.save(entry);

        log.info("Sync finished (success={}): tutors={}(-{}) services={}(-{}) "
                        + "students={}(-{}) bookings={}(-{})",
                entry.isSuccess(),
                entry.getTutorsUpserted(),   entry.getTutorsRemoved(),
                entry.getServicesUpserted(), entry.getServicesRemoved(),
                entry.getStudentsUpserted(), entry.getStudentsRemoved(),
                entry.getBookingsUpserted(), entry.getBookingsRemoved());
        return entry;
    }

    private void runStep(String name, Runnable step, List<String> failures) {
        try {
            // Each step is atomic: if it throws partway through, its writes roll back
            // so the step never leaves half-synced data behind. Other steps are
            // unaffected because each gets its own transaction.
            transactionTemplate.executeWithoutResult(status -> step.run());
        } catch (Exception e) {
            log.error("Sync step '{}' failed: {}", name, e.getMessage(), e);
            failures.add(name + ": " + e.getMessage());
        }
    }

    // Counters are recorded only after every write in the step has succeeded, so a
    // rolled-back step never reports phantom upsert/removal counts in the SyncLog.

    private void syncTutors(SyncLog entry) {
        List<Tutor> tutors = performerAdapter.toTutors(client.getPerformerList());
        tutorRepo.saveAll(tutors);
        int removed = reconcileDeletions(
                tutorRepo.findAll(), tutors,
                Tutor::getId, Tutor::getDeletedAt, Tutor::setDeletedAt, tutorRepo);
        entry.setTutorsUpserted(tutors.size());
        entry.setTutorsRemoved(removed);
    }

    private void syncServices(SyncLog entry) {
        List<ca.vicilearning.dashboard.domain.Service> services =
                serviceAdapter.toServices(client.getServiceList());
        serviceRepo.saveAll(services);
        int removed = reconcileDeletions(
                serviceRepo.findAll(), services,
                ca.vicilearning.dashboard.domain.Service::getId,
                ca.vicilearning.dashboard.domain.Service::getDeletedAt,
                ca.vicilearning.dashboard.domain.Service::setDeletedAt, serviceRepo);
        entry.setServicesUpserted(services.size());
        entry.setServicesRemoved(removed);
    }

    private void syncStudents(SyncLog entry) {
        List<Student> students = clientAdapter.toStudents(client.getClientList());
        studentRepo.saveAll(students);
        int removed = reconcileDeletions(
                studentRepo.findAll(), students,
                Student::getId, Student::getDeletedAt, Student::setDeletedAt, studentRepo);
        entry.setStudentsUpserted(students.size());
        entry.setStudentsRemoved(removed);
    }

    private void syncBookings(SyncLog entry) {
        Map<Long, Student> studentMap = toMap(studentRepo.findAll(), Student::getId);
        Map<Long, Tutor>   tutorMap   = toMap(tutorRepo.findAll(),   Tutor::getId);
        Map<Long, ca.vicilearning.dashboard.domain.Service> serviceMap =
                toMap(serviceRepo.findAll(), ca.vicilearning.dashboard.domain.Service::getId);

        LocalDate from = LocalDate.now(ZoneOffset.UTC).minusDays(BOOKING_LOOKBACK_DAYS);
        LocalDate to   = LocalDate.now(ZoneOffset.UTC).plusDays(BOOKING_LOOKAHEAD_DAYS);

        List<Booking> bookings = bookingAdapter.toBookings(
                client.getBookingList(from, to), studentMap, tutorMap, serviceMap);
        bookingRepo.saveAll(bookings);

        // Only reconcile within the queried window: a booking outside [from, to] was
        // never fetched, so its absence from the result does not imply it was removed.
        List<Booking> existingInWindow = bookingRepo.findByStartTimeBetween(
                from.atStartOfDay(), to.atTime(LocalTime.MAX));
        int removed = reconcileDeletions(
                existingInWindow, bookings,
                Booking::getId, Booking::getDeletedAt, Booking::setDeletedAt, bookingRepo);
        entry.setBookingsUpserted(bookings.size());
        entry.setBookingsRemoved(removed);
    }

    private <T, K> Map<K, T> toMap(List<T> list, Function<T, K> keyFn) {
        return list.stream().collect(Collectors.toMap(keyFn, t -> t));
    }

    /**
     * Soft-deletes local rows that are no longer present upstream. Any row in
     * {@code existing} whose id is not in {@code fetched} and is not already marked
     * deleted gets its {@code deletedAt} stamped and is saved. Rows that reappear
     * upstream are un-deleted automatically: the upsert of {@code fetched} writes a
     * fresh entity with {@code deletedAt == null} before this runs.
     *
     * @return the number of rows newly marked deleted by this call
     */
    private <T> int reconcileDeletions(List<T> existing,
                                       List<T> fetched,
                                       Function<T, Long> idFn,
                                       Function<T, LocalDateTime> getDeletedAt,
                                       BiConsumer<T, LocalDateTime> setDeletedAt,
                                       JpaRepository<T, Long> repo) {
        Set<Long> liveIds = fetched.stream().map(idFn).collect(Collectors.toSet());
        List<T> newlyRemoved = existing.stream()
                .filter(row -> !liveIds.contains(idFn.apply(row)))
                .filter(row -> getDeletedAt.apply(row) == null)
                .toList();

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        newlyRemoved.forEach(row -> setDeletedAt.accept(row, now));
        repo.saveAll(newlyRemoved);
        return newlyRemoved.size();
    }
}
