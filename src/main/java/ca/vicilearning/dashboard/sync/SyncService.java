package ca.vicilearning.dashboard.sync;

import ca.vicilearning.dashboard.comms.BrevoCommunicationService;
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
    private final SimplybookRestClient    restClient;
    private final SimplybookProperties    props;
    private final ClientAdapter           clientAdapter;
    private final PerformerAdapter        performerAdapter;
    private final ServiceAdapter          serviceAdapter;
    private final BookingAdapter          bookingAdapter;
    private final InvoiceAdapter          invoiceAdapter;
    private final MembershipAdapter       membershipAdapter;
    private final StudentRepository       studentRepo;
    private final TutorRepository         tutorRepo;
    private final ServiceRepository       serviceRepo;
    private final BookingRepository       bookingRepo;
    private final InvoiceRepository       invoiceRepo;
    private final MembershipRepository    membershipRepo;
    private final SyncLogRepository       syncLogRepo;
    private final BrevoCommunicationService brevoService;

    // Ensures only one sync runs at a time: the hourly scheduler and a manual
    // "Sync Now" click can otherwise race on the same tables.
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

    // Runs each step in its own transaction. We use TransactionTemplate rather than
    // @Transactional because the steps are invoked via self-invocation from doSync(),
    // which Spring's proxy-based @Transactional does not intercept.
    private final TransactionTemplate transactionTemplate;

    public SyncService(SimplybookClient client,
                       SimplybookRestClient restClient,
                       SimplybookProperties props,
                       ClientAdapter clientAdapter,
                       PerformerAdapter performerAdapter,
                       ServiceAdapter serviceAdapter,
                       BookingAdapter bookingAdapter,
                       InvoiceAdapter invoiceAdapter,
                       MembershipAdapter membershipAdapter,
                       StudentRepository studentRepo,
                       TutorRepository tutorRepo,
                       ServiceRepository serviceRepo,
                       BookingRepository bookingRepo,
                       InvoiceRepository invoiceRepo,
                       MembershipRepository membershipRepo,
                       SyncLogRepository syncLogRepo,
                       BrevoCommunicationService brevoService,
                       PlatformTransactionManager transactionManager) {
        this.client          = client;
        this.restClient      = restClient;
        this.props           = props;
        this.clientAdapter   = clientAdapter;
        this.performerAdapter = performerAdapter;
        this.serviceAdapter  = serviceAdapter;
        this.bookingAdapter  = bookingAdapter;
        this.invoiceAdapter  = invoiceAdapter;
        this.membershipAdapter = membershipAdapter;
        this.studentRepo     = studentRepo;
        this.tutorRepo       = tutorRepo;
        this.serviceRepo     = serviceRepo;
        this.bookingRepo     = bookingRepo;
        this.invoiceRepo     = invoiceRepo;
        this.membershipRepo  = membershipRepo;
        this.syncLogRepo     = syncLogRepo;
        this.brevoService    = brevoService;
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
        // Runs after students so their rows exist; uses REST v2 (not JSON-RPC) for the
        // Account_ID custom field. Its own step so a REST outage can't fail booking sync.
        runStep("accountIds", () -> syncAccountIds(entry), failures);
        // Matches local students to Brevo contacts (by email) to stamp the EXT_ID per-student id.
        // Own step so a Brevo outage/missing key can't fail the SimplyBook data above.
        runStep("extIds", () -> syncExtIds(entry), failures);
        // Invoices and memberships also come from REST v2 and link back to students, so they
        // run after the student sync. Each is its own step: a REST outage or a bad record on
        // one must not fail the other or the JSON-RPC data synced above.
        runStep("invoices",    () -> syncInvoices(entry),    failures);
        runStep("memberships", () -> syncMemberships(entry), failures);

        entry.setSuccess(failures.isEmpty());
        if (!failures.isEmpty()) {
            entry.setErrorMessage(String.join("; ", failures));
        }
        entry.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
        syncLogRepo.save(entry);

        log.info("Sync finished (success={}): tutors={}(-{}) services={}(-{}) "
                        + "students={}(-{}) bookings={}(-{}) invoices={}(-{}) "
                        + "memberships={}(-{}) accountIdsLinked={} extIdsLinked={}",
                entry.isSuccess(),
                entry.getTutorsUpserted(),   entry.getTutorsRemoved(),
                entry.getServicesUpserted(), entry.getServicesRemoved(),
                entry.getStudentsUpserted(), entry.getStudentsRemoved(),
                entry.getBookingsUpserted(), entry.getBookingsRemoved(),
                entry.getInvoicesUpserted(), entry.getInvoicesRemoved(),
                entry.getMembershipsUpserted(), entry.getMembershipsRemoved(),
                entry.getAccountIdsLinked(), entry.getExtIdsLinked());
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

        // The JSON-RPC client list cannot read the Account_ID custom field (nor EXT_ID, which
        // lives in Brevo), so every freshly adapted student has accountId == null and extId ==
        // null. Carry over any values we previously resolved before the upsert; otherwise
        // saveAll's merge would blank those columns each sync and force the linking steps to
        // re-resolve every student. Preserving them lets those steps work only the unlinked backlog.
        List<Student> existing = studentRepo.findAll();
        Map<Long, String> knownAccountIds = existing.stream()
                .filter(s -> s.getAccountId() != null)
                .collect(Collectors.toMap(Student::getId, Student::getAccountId));
        Map<Long, String> knownExtIds = existing.stream()
                .filter(s -> s.getExtId() != null)
                .collect(Collectors.toMap(Student::getId, Student::getExtId));
        // Status is a local/Brevo-held flag SimplyBook never returns, so a freshly adapted student
        // always carries the default ACTIVE. Carry over the stored status so a staff-set PAUSED
        // isn't reset to ACTIVE on every sync.
        Map<Long, StudentStatus> knownStatuses = existing.stream()
                .filter(s -> s.getStatus() != null)
                .collect(Collectors.toMap(Student::getId, Student::getStatus));
        for (Student s : students) {
            String knownAccount = knownAccountIds.get(s.getId());
            if (knownAccount != null) {
                s.setAccountId(knownAccount);
            }
            String knownExt = knownExtIds.get(s.getId());
            if (knownExt != null) {
                s.setExtId(knownExt);
            }
            StudentStatus knownStatus = knownStatuses.get(s.getId());
            if (knownStatus != null) {
                s.setStatus(knownStatus);
            }
        }

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

    /**
     * Populates the Account_ID (the Brevo link) for students that don't have one yet, from
     * SimplyBook REST v2. This is one REST call per <em>unlinked</em> student, not per student:
     * syncStudents preserves already-resolved Account_IDs across upserts, so at steady state this
     * step makes zero calls and only works through the backlog of newly-added students. That is
     * what lets the sync scale toward the 300+/1000+ student targets without hammering REST v2
     * every hour. A failure for one client is logged and skipped so a single bad record never
     * aborts the whole step.
     *
     * <p>Trade-off: an Account_ID that is <em>changed</em> upstream after we first read it is not
     * re-fetched here. The field is created once and then stable in practice; if it ever needs to
     * be re-pulled, clear the local value (set it null) and the next sync will resolve it again.
     *
     * <p>Skipped cleanly (not failed) when REST creds aren't configured yet, so teammates
     * without REST keys still get green syncs for the JSON-RPC data.
     */
    private void syncAccountIds(SyncLog entry) {
        if (!props.restConfigured()) {
            log.info("REST v2 not configured (no company login / API key); skipping Account_ID sync");
            entry.setAccountIdsLinked(0);
            return;
        }

        List<Student> students = studentRepo.findByDeletedAtIsNullAndAccountIdIsNull();
        String fieldTitle = props.accountIdFieldTitle();
        int linked = 0;

        for (Student student : students) {
            String accountId;
            try {
                accountId = clientAdapter.extractAccountId(
                        restClient.getClientFieldValues(student.getId()), fieldTitle);
            } catch (Exception e) {
                log.warn("Account_ID fetch failed for client {}: {}", student.getId(), e.getMessage());
                continue;
            }
            // These students had no Account_ID, so any non-blank value we read is new.
            if (accountId != null) {
                student.setAccountId(accountId);
                studentRepo.save(student);
                linked++;
            }
        }
        entry.setAccountIdsLinked(linked);
    }

    /**
     * Stamps each student's EXT_ID (the Brevo per-student unique id) by matching the student's
     * email to a Brevo contact. Backlog-only: {@link #syncStudents} carries EXT_IDs across upserts,
     * so at steady state this only fills newly-added students. Skipped cleanly (not failed) when
     * Brevo returns nothing — no API key, an outage, or simply no contacts — so teammates without a
     * Brevo key still get green syncs for the SimplyBook data.
     */
    private void syncExtIds(SyncLog entry) {
        Map<String, String> emailToExtId = brevoService.fetchEmailToExtIdMap();
        if (emailToExtId == null || emailToExtId.isEmpty()) {
            log.info("Brevo returned no contacts (no key configured?); skipping EXT_ID sync");
            entry.setExtIdsLinked(0);
            return;
        }

        int linked = 0;
        for (Student student : studentRepo.findByDeletedAtIsNullAndExtIdIsNull()) {
            if (student.getEmail() == null || student.getEmail().isBlank()) {
                continue;
            }
            String extId = emailToExtId.get(student.getEmail().trim().toLowerCase());
            if (extId != null && !extId.isBlank()) {
                student.setExtId(extId);
                studentRepo.save(student);
                linked++;
            }
        }
        entry.setExtIdsLinked(linked);
    }

    /**
     * Syncs invoices from REST v2 for the "unpaid families" rule and the cash-flow overview.
     * Skipped cleanly (not failed) when REST creds aren't configured, mirroring the Account_ID
     * step, so teammates without REST keys still get green syncs for the JSON-RPC data.
     *
     * <p>Unlike bookings there is no date window: {@link SimplybookRestClient#getAllInvoices()}
     * returns every invoice, so a full reconcile is correct — any local invoice absent from the
     * result was genuinely removed upstream.
     */
    private void syncInvoices(SyncLog entry) {
        if (!props.restConfigured()) {
            log.info("REST v2 not configured; skipping invoice sync");
            entry.setInvoicesUpserted(0);
            entry.setInvoicesRemoved(0);
            return;
        }
        Map<Long, Student> studentMap = toMap(studentRepo.findAll(), Student::getId);
        List<Invoice> invoices = invoiceAdapter.toInvoices(restClient.getAllInvoices(), studentMap);
        invoiceRepo.saveAll(invoices);
        int removed = reconcileDeletions(
                invoiceRepo.findAll(), invoices,
                Invoice::getId, Invoice::getDeletedAt, Invoice::setDeletedAt, invoiceRepo);
        entry.setInvoicesUpserted(invoices.size());
        entry.setInvoicesRemoved(removed);
    }

    /**
     * Syncs client memberships from REST v2. (Originally framed as "can't book at 0" balances;
     * the credit model is unconfirmed — see {@link ca.vicilearning.dashboard.domain.Membership}.)
     * Same gating and full-reconcile rationale as {@link #syncInvoices}.
     */
    private void syncMemberships(SyncLog entry) {
        if (!props.restConfigured()) {
            log.info("REST v2 not configured; skipping membership sync");
            entry.setMembershipsUpserted(0);
            entry.setMembershipsRemoved(0);
            return;
        }
        Map<Long, Student> studentMap = toMap(studentRepo.findAll(), Student::getId);
        List<Membership> memberships =
                membershipAdapter.toMemberships(restClient.getAllMemberships(), studentMap);
        membershipRepo.saveAll(memberships);
        int removed = reconcileDeletions(
                membershipRepo.findAll(), memberships,
                Membership::getId, Membership::getDeletedAt, Membership::setDeletedAt, membershipRepo);
        entry.setMembershipsUpserted(memberships.size());
        entry.setMembershipsRemoved(removed);
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
