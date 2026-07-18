package ca.vicilearning.dashboard.metrics;

import ca.vicilearning.dashboard.domain.Booking;
import ca.vicilearning.dashboard.domain.BookingRepository;
import ca.vicilearning.dashboard.domain.Invoice;
import ca.vicilearning.dashboard.domain.InvoiceRepository;
import ca.vicilearning.dashboard.domain.Membership;
import ca.vicilearning.dashboard.domain.MembershipRepository;
import ca.vicilearning.dashboard.domain.ServiceRepository;
import ca.vicilearning.dashboard.domain.Student;
import ca.vicilearning.dashboard.domain.StudentRepository;
import ca.vicilearning.dashboard.domain.StudentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Computes dashboard metrics from the local database — the layer that turns synced/seeded
 * rows into the numbers Sara cares about (weekly hours, active students, cancellations,
 * upcoming sessions, per-tutor totals). Everything reads only local data, so pages stay fast
 * and work offline.
 *
 * <p>Hours are always derived from each booking's duration (a session can be 1h or 2h), never
 * assumed to be one hour — a direct client requirement.
 */
@Service
public class DashboardMetricsService {

    private final BookingRepository bookingRepo;
    private final StudentRepository studentRepo;
    private final InvoiceRepository invoiceRepo;
    private final ServiceRepository serviceRepo;
    private final MembershipRepository membershipRepo;

    // A family with this many (or fewer) prepaid sessions left is "running low" and gets flagged;
    // 0 is a separate, higher-priority "can't book" alert. Configurable so staff can tune it once a
    // rules engine exists. (Meeting #3 confirmed the credit model, so this alerting is valid.)
    private final int membershipLowThreshold;

    // A student with no booking in at least this many days is "lapsed" — the top follow-up target.
    // One configurable value shared with BrevoSyncEngineService so the dashboard action items and
    // the Brevo reconciliation agree on who's lapsed (previously 21 here vs 14 there).
    private final int lapseThresholdDays;

    // Action-item severities (sort key, higher = shown first). Membership problems rank high
    // because a family at/near 0 literally can't book more sessions.
    private static final int SEVERITY_MEMBERSHIP_EMPTY = 1000;
    private static final int SEVERITY_MEMBERSHIP_LOW = 500;

    public DashboardMetricsService(BookingRepository bookingRepo, StudentRepository studentRepo,
                                    InvoiceRepository invoiceRepo, ServiceRepository serviceRepo,
                                    MembershipRepository membershipRepo,
                                    @Value("${metrics.membership-low-threshold:2}") int membershipLowThreshold,
                                    @Value("${metrics.lapse-threshold-days:21}") int lapseThresholdDays) {
        this.bookingRepo = bookingRepo;
        this.studentRepo = studentRepo;
        this.invoiceRepo = invoiceRepo;
        this.serviceRepo = serviceRepo;
        this.membershipRepo = membershipRepo;
        this.membershipLowThreshold = membershipLowThreshold;
        this.lapseThresholdDays = lapseThresholdDays;
    }

    /**
     * Distinct non-blank service <b>locations</b> — the delivery mode from SimplyBook (At Home /
     * Virtual Tutoring / VICI Learning Centre / Study Clubs), sorted, for the Location filter
     * dropdown. Reads active services only. (Meeting #4: these are locations, not categories.)
     */
    public List<String> serviceLocations() {
        return distinctServiceAttr(ca.vicilearning.dashboard.domain.Service::getLocation);
    }

    /**
     * Distinct non-blank service <b>categories</b> — the session type from SimplyBook
     * (Private 1:1 / Study Club / Assessment), sorted, for the Category filter dropdown.
     * A separate axis from {@link #serviceLocations()} (Meeting #4).
     */
    public List<String> serviceCategories() {
        return distinctServiceAttr(ca.vicilearning.dashboard.domain.Service::getCategory);
    }

    private List<String> distinctServiceAttr(
            java.util.function.Function<ca.vicilearning.dashboard.domain.Service, String> attr) {
        return serviceRepo.findByDeletedAtIsNull().stream()
                .map(attr)
                .filter(Objects::nonNull)
                .filter(v -> !v.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    // ── Public metrics ─────────────────────────────────────────────────────────

    /** Headline overview cards for the current week / month (all services). */
    public Overview overview() {
        return overview(null);
    }

    /**
     * Headline overview cards, optionally scoped to a {@link ServiceScope} (location and/or
     * category). When a scope is set, sessions/hours/cancellations count only matching bookings and
     * "active students" becomes the number of students with a matching booking, so the whole
     * overview reflects the same page-wide filter.
     */
    public Overview overview(ServiceScope scope) {
        LocalDate today = today();
        LocalDate weekStart = weekStart(today);

        List<Booking> thisWeek = activeBetween(weekStart, weekStart.plusWeeks(1));
        int sessions = (int) thisWeek.stream()
                .filter(b -> isCounted(b) && matches(b, scope)).count();
        double hours = thisWeek.stream()
                .filter(b -> isCounted(b) && matches(b, scope)).mapToDouble(this::hoursOf).sum();

        LocalDate monthStart = today.withDayOfMonth(1);
        long cancellations = activeBetween(monthStart, monthStart.plusMonths(1)).stream()
                .filter(b -> isCancelled(b) && matches(b, scope)).count();

        long activeStudents = isAll(scope)
                ? studentRepo.countByDeletedAtIsNull()
                : studentIdsMatching(scope).size();

        return new Overview(activeStudents, sessions, round1(hours), (int) cancellations);
    }

    /**
     * Hours and session counts per ISO week, from {@code weeksBack} before the current week
     * through {@code weeksAhead} after it. Empty weeks are included (zeroes) so a chart/table
     * shows a continuous timeline. This is the automated replacement for the spreadsheet's
     * hand-typed weekly "BOOKED" columns.
     *
     * @deprecated thin wrapper kept for existing callers/tests — prefer {@link #hoursByPeriod}.
     */
    @Deprecated
    public List<WeeklyHours> weeklyHours(int weeksBack, int weeksAhead) {
        return hoursByPeriod(PeriodUnit.WEEK, weeksBack, weeksAhead).stream()
                .map(p -> new WeeklyHours(p.periodStart(), p.hours(), p.sessions()))
                .toList();
    }

    /**
     * Hours and session counts bucketed by {@code unit} (week/month/year), from {@code periodsBack}
     * before the current bucket through {@code periodsAhead} after it. Empty buckets are included
     * (zeroes) so a chart/table shows a continuous timeline — this backs the client's week/month/year
     * filter (the "BOOKED &lt;date&gt;" columns she used to fill in by hand, now computed at any granularity).
     */
    public List<PeriodHours> hoursByPeriod(PeriodUnit unit, int periodsBack, int periodsAhead) {
        return hoursByPeriod(unit, periodsBack, periodsAhead, null);
    }

    /**
     * As {@link #hoursByPeriod(PeriodUnit, int, int)} but restricted to bookings matching the given
     * {@link ServiceScope} (location and/or category). A null/empty scope means no filter. Backs
     * Sara's "filter hours booked by location/category" ask.
     */
    public List<PeriodHours> hoursByPeriod(PeriodUnit unit, int periodsBack, int periodsAhead, ServiceScope scope) {
        LocalDate currentBucket = bucketStart(unit, today());
        LocalDate firstBucket = stepBucket(unit, currentBucket, -periodsBack);
        LocalDate endExclusive = stepBucket(unit, currentBucket, periodsAhead + 1L);

        // Seed every bucket with zeroes so gaps render as 0 rather than disappearing.
        Map<LocalDate, double[]> byBucket = new LinkedHashMap<>(); // [hours, sessions]
        for (LocalDate b = firstBucket; b.isBefore(endExclusive); b = stepBucket(unit, b, 1)) {
            byBucket.put(b, new double[]{0.0, 0.0});
        }

        for (Booking bk : activeBetween(firstBucket, endExclusive)) {
            if (!isCounted(bk) || !matches(bk, scope)) continue;
            LocalDate bucket = bucketStart(unit, bk.getStartTime().toLocalDate());
            double[] cell = byBucket.get(bucket);
            if (cell != null) {
                cell[0] += hoursOf(bk);
                cell[1] += 1;
            }
        }

        List<PeriodHours> out = new ArrayList<>();
        byBucket.forEach((b, cell) -> out.add(new PeriodHours(b, round1(cell[0]), (int) cell[1])));
        return out;
    }

    /**
     * Total hours/sessions over an arbitrary, unbucketed date range — backs the client's
     * "date range" filter option (a single totals summary, not a chart bucket).
     */
    public RangeHours hoursInRange(LocalDate fromInclusive, LocalDate toExclusive) {
        return hoursInRange(fromInclusive, toExclusive, null);
    }

    /** As {@link #hoursInRange(LocalDate, LocalDate)} but restricted to a {@link ServiceScope} filter. */
    public RangeHours hoursInRange(LocalDate fromInclusive, LocalDate toExclusive, ServiceScope scope) {
        List<Booking> bookings = activeBetween(fromInclusive, toExclusive).stream()
                .filter(this::isCounted)
                .filter(b -> matches(b, scope))
                .toList();
        double hours = bookings.stream().mapToDouble(this::hoursOf).sum();
        return new RangeHours(round1(hours), bookings.size());
    }

    /** Next {@code limit} upcoming (non-cancelled) sessions, soonest first (all locations). */
    public List<UpcomingSession> upcoming(int limit) {
        return upcoming(limit, null);
    }

    /** As {@link #upcoming(int)} but restricted to a {@link ServiceScope} (null/empty = all). */
    public List<UpcomingSession> upcoming(int limit, ServiceScope scope) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return bookingRepo.findActiveWithRefsBetween(now, now.plusDays(60)).stream()
                .filter(this::isCounted)
                .filter(b -> matches(b, scope))
                .sorted(Comparator.comparing(Booking::getStartTime))
                .limit(limit)
                .map(b -> new UpcomingSession(
                        b.getStudent().getName(),
                        b.getTutor() != null ? b.getTutor().getName() : "Unassigned",
                        b.getService().getName(),
                        b.getStartTime(),
                        b.getStatus()))
                .toList();
    }

    /**
     * Hours booked this week grouped by tutor (the "tutor drill-down" Sara called her killer
     * feature), sorted by total hours descending.
     *
     * @deprecated thin wrapper kept for existing callers/tests — prefer {@link #tutorHoursForPeriod}.
     */
    @Deprecated
    public List<TutorHours> tutorHoursThisWeek() {
        LocalDate weekStart = weekStart(today());
        return tutorHoursForRange(weekStart, weekStart.plusWeeks(1), false);
    }

    /** Per-tutor hours/sessions for the current bucket of {@code unit} (this week/month/year). */
    public List<TutorHours> tutorHoursForPeriod(PeriodUnit unit, boolean sortByName) {
        return tutorHoursForPeriod(unit, sortByName, null);
    }

    /** As {@link #tutorHoursForPeriod(PeriodUnit, boolean)} but restricted to a {@link ServiceScope}. */
    public List<TutorHours> tutorHoursForPeriod(PeriodUnit unit, boolean sortByName, ServiceScope scope) {
        LocalDate start = bucketStart(unit, today());
        return tutorHoursForRange(start, stepBucket(unit, start, 1), sortByName, scope);
    }

    /**
     * Per-tutor hours/sessions over an arbitrary date range. Sorted by total hours descending
     * by default (her "sort by total"), or alphabetically when {@code sortByName} is set
     * (her "sort by tutor").
     */
    public List<TutorHours> tutorHoursForRange(LocalDate fromInclusive, LocalDate toExclusive, boolean sortByName) {
        return tutorHoursForRange(fromInclusive, toExclusive, sortByName, null);
    }

    /** As {@link #tutorHoursForRange(LocalDate, LocalDate, boolean)} but with a {@link ServiceScope} filter. */
    public List<TutorHours> tutorHoursForRange(LocalDate fromInclusive, LocalDate toExclusive,
                                               boolean sortByName, ServiceScope scope) {
        Map<String, double[]> byTutor = new LinkedHashMap<>();
        for (Booking b : activeBetween(fromInclusive, toExclusive)) {
            if (!isCounted(b) || !matches(b, scope)) continue;
            String tutor = b.getTutor() != null ? b.getTutor().getName() : "Unassigned";
            double[] cell = byTutor.computeIfAbsent(tutor, k -> new double[]{0.0, 0.0});
            cell[0] += hoursOf(b);
            cell[1] += 1;
        }
        List<TutorHours> out = new ArrayList<>();
        byTutor.forEach((tutor, cell) -> out.add(new TutorHours(tutor, round1(cell[0]), (int) cell[1])));
        if (sortByName) {
            out.sort(Comparator.comparing(TutorHours::tutorName, String.CASE_INSENSITIVE_ORDER));
        } else {
            out.sort(Comparator.comparingDouble(TutorHours::hours).reversed());
        }
        return out;
    }

    /**
     * One row per active student with this week's booked sessions/hours — the spreadsheet
     * Brevo+SimplyBook join made live: identity + Account_ID alongside computed weekly hours.
     */
    public List<StudentRow> studentRows() {
        return studentRows(null);
    }

    /**
     * As {@link #studentRows()} but scoped to a {@link ServiceScope}: only students with a matching
     * booking are returned, and their weekly sessions/hours count only matching bookings — so the
     * roster matches the page-wide location/category filter.
     */
    public List<StudentRow> studentRows(ServiceScope scope) {
        return studentRows(scope, null);
    }

    /**
     * As {@link #studentRows(ServiceScope)} but additionally filtered by enrolment status: when
     * {@code statusFilter} is non-null only students with that {@link StudentStatus} are returned
     * (null = both). Backs the Students roster's ACTIVE/PAUSED filter (Meeting #4). The status is
     * carried on each row so the view can badge it.
     */
    public List<StudentRow> studentRows(ServiceScope scope, StudentStatus statusFilter) {
        Map<Long, double[]> byStudent = hoursThisWeekByStudent(scope);
        Set<Long> matching = isAll(scope) ? null : studentIdsMatching(scope);

        return studentRepo.findByDeletedAtIsNull().stream()
                .filter(s -> matching == null || matching.contains(s.getId()))
                .filter(s -> statusFilter == null || s.getStatus() == statusFilter)
                .sorted(Comparator.comparing(Student::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(s -> {
                    double[] cell = byStudent.getOrDefault(s.getId(), new double[]{0.0, 0.0});
                    return new StudentRow(
                            s.getId(), s.getName(), s.getAccountId(), s.getExtId(), s.getEmail(), s.getPhone(),
                            (int) cell[1], round1(cell[0]), s.getStatus());
                })
                .toList();
    }

    /**
     * Active students rolled up by shared Account_ID — the "sibling" view. Two students that
     * share an {@code accountId} (the SimplyBook.me custom field linking them to one Brevo
     * account) are siblings in the same family; this groups them so the client can see
     * Account_ID → [Student A, Student B, …] at a glance without any new integration.
     *
     * <p>Only accounts with 2+ active students are returned (a lone student isn't a "family"),
     * and students with no Account_ID are skipped (they can't be grouped). Groups are sorted by
     * Account_ID, members by name, and each group carries this week's combined hours/sessions.
     */
    public List<FamilyGroup> familyGroups() {
        return familyGroups(null);
    }

    /**
     * As {@link #familyGroups()} but scoped to a {@link ServiceScope}: only families with at least
     * one matching-booked member are returned, and weekly hours count only matching bookings — so
     * the Families rollup matches the page-wide location/category filter.
     */
    public List<FamilyGroup> familyGroups(ServiceScope scope) {
        Map<Long, double[]> byStudent = hoursThisWeekByStudent(scope);
        Set<Long> matching = isAll(scope) ? null : studentIdsMatching(scope);

        // Per-student service categories/locations (from all bookings) and each student's latest
        // membership balance, built once so the rollup below is O(students) rather than per-family.
        Map<Long, Set<String>> categoriesByStudent = new LinkedHashMap<>();
        Map<Long, Set<String>> locationsByStudent = new LinkedHashMap<>();
        collectServiceAttrs(categoriesByStudent, locationsByStudent);
        Map<Long, Integer> latestBalanceByStudent = latestMembershipBalanceByStudent(false);

        // Preserve insertion order per key so members stay sorted by the pre-sorted stream below.
        Map<String, List<FamilyMember>> byAccount = new LinkedHashMap<>();
        studentRepo.findByDeletedAtIsNull().stream()
                .filter(s -> s.getAccountId() != null && !s.getAccountId().isBlank())
                .sorted(Comparator.comparing(Student::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .forEach(s -> {
                    double[] cell = byStudent.getOrDefault(s.getId(), new double[]{0.0, 0.0});
                    byAccount.computeIfAbsent(s.getAccountId(), k -> new ArrayList<>())
                            .add(new FamilyMember(s.getId(), s.getName(), s.getExtId(), s.getEmail(), s.getPhone(),
                                    (int) cell[1], round1(cell[0])));
                });

        return byAccount.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .filter(e -> matching == null
                        || e.getValue().stream().anyMatch(m -> matching.contains(m.id())))
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(e -> {
                    List<FamilyMember> members = e.getValue();
                    int sessions = members.stream().mapToInt(FamilyMember::sessionsThisWeek).sum();
                    double hours = members.stream().mapToDouble(FamilyMember::hoursThisWeek).sum();

                    // Union the compact distinct lists across the family's members (Meeting #3 rows).
                    // Balances are each member's LATEST membership only (Meeting #4).
                    Set<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                    Set<String> locations = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                    List<Integer> balances = new ArrayList<>();
                    for (FamilyMember m : members) {
                        categories.addAll(categoriesByStudent.getOrDefault(m.id(), Set.of()));
                        locations.addAll(locationsByStudent.getOrDefault(m.id(), Set.of()));
                        Integer bal = latestBalanceByStudent.get(m.id());
                        if (bal != null) balances.add(bal);
                    }
                    return new FamilyGroup(e.getKey(), members, sessions, round1(hours),
                            List.copyOf(categories), List.copyOf(locations), balances);
                })
                .toList();
    }

    /** Fills {@code categories}/{@code locations} maps: studentId → distinct service attrs from bookings. */
    private void collectServiceAttrs(Map<Long, Set<String>> categories, Map<Long, Set<String>> locations) {
        for (Booking b : bookingRepo.findActiveWithStudentAndService()) {
            if (!isCounted(b) || b.getStudent() == null || b.getService() == null) continue;
            Long sid = b.getStudent().getId();
            addAttr(categories, sid, b.getService().getCategory());
            addAttr(locations, sid, b.getService().getLocation());
        }
    }

    private void addAttr(Map<Long, Set<String>> map, Long studentId, String value) {
        if (value == null || value.isBlank()) return;
        map.computeIfAbsent(studentId, k -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)).add(value.trim());
    }

    /**
     * studentId → the remaining prepaid-session balance of the student's <b>latest</b> membership
     * (the most recently purchased, by {@code startDate}). Sara confirmed (Meeting #4) the counter
     * should reflect the latest membership they bought, not every membership they've ever held.
     * When {@code activeOnly}, cancelled memberships are ignored — used by the low/zero-balance
     * alerting, which shouldn't flag a family whose latest active membership is fine.
     */
    private Map<Long, Integer> latestMembershipBalanceByStudent(boolean activeOnly) {
        Map<Long, Membership> latest = new LinkedHashMap<>();
        for (Membership m : membershipRepo.findByDeletedAtIsNull()) {
            // getStudent() returns the lazy proxy (or null); getId() on it needs no DB hit.
            if (m.getStudent() == null || m.getRemainingCount() == null) continue;
            if (activeOnly && !m.isActive()) continue;
            Long sid = m.getStudent().getId();
            Membership incumbent = latest.get(sid);
            if (incumbent == null || isLater(m.getStartDate(), incumbent.getStartDate())) {
                latest.put(sid, m);
            }
        }
        Map<Long, Integer> out = new LinkedHashMap<>();
        latest.forEach((sid, m) -> out.put(sid, m.getRemainingCount()));
        return out;
    }

    /** True when {@code candidate} is a later start date than {@code incumbent} (nulls sort earliest). */
    private boolean isLater(LocalDateTime candidate, LocalDateTime incumbent) {
        if (candidate == null) return false;
        if (incumbent == null) return true;
        return candidate.isAfter(incumbent);
    }

    /**
     * This week's booked [hours, sessions] per active student id, cancellations excluded and
     * optionally restricted to a {@link ServiceScope} (null/empty = all).
     */
    private Map<Long, double[]> hoursThisWeekByStudent(ServiceScope scope) {
        LocalDate weekStart = weekStart(today());
        Map<Long, double[]> byStudent = new LinkedHashMap<>(); // [hours, sessions]
        for (Booking b : activeBetween(weekStart, weekStart.plusWeeks(1))) {
            if (!isCounted(b) || !matches(b, scope)) continue;
            double[] cell = byStudent.computeIfAbsent(b.getStudent().getId(), k -> new double[]{0.0, 0.0});
            cell[0] += hoursOf(b);
            cell[1] += 1;
        }
        return byStudent;
    }

    /** True when {@code scope} means "everything" (no filter). */
    private boolean isAll(ServiceScope scope) {
        return scope == null || scope.isAll();
    }

    /** Distinct ids of active students with at least one non-cancelled booking matching {@code scope}. */
    private Set<Long> studentIdsMatching(ServiceScope scope) {
        Set<Long> ids = new HashSet<>();
        for (Booking b : bookingRepo.findActiveWithStudentAndService()) {
            if (isCounted(b) && matches(b, scope) && b.getStudent() != null) {
                ids.add(b.getStudent().getId());
            }
        }
        return ids;
    }

    /**
     * Students/families needing attention:
     *  - no booking in {@code metrics.lapse-threshold-days}+ days (from their most recent active booking)
     *  - 3+ cancellations this calendar month
     *  - membership balance at 0 ("can't book") or at/below the configurable low threshold
     * Sorted worst-first by severity (membership problems rank highest).
     */
    public List<ActionItem> actionRequired() {
        LocalDate monthStart = today().withDayOfMonth(1);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        Map<Long, LocalDateTime> lastBookingByStudent = new LinkedHashMap<>();
        Map<Long, Integer> cancellationsThisMonth = new LinkedHashMap<>();
        Map<Long, Integer> latestBalanceByStudent = latestMembershipBalanceByStudent(true);

        for (Booking b : bookingRepo.findByDeletedAtIsNull()) {
            Long sid = b.getStudent().getId();

            if (isCancelled(b) && !b.getStartTime().toLocalDate().isBefore(monthStart)) {
                cancellationsThisMonth.merge(sid, 1, Integer::sum);
            }

            if (isCounted(b) && b.getStartTime().isBefore(now)) {
                lastBookingByStudent.merge(sid, b.getStartTime(),
                        (existing, candidate) -> candidate.isAfter(existing) ? candidate : existing);
            }
        }

        List<ActionItem> items = new ArrayList<>();

        for (Student s : studentRepo.findByDeletedAtIsNull()) {
            LocalDateTime last = lastBookingByStudent.get(s.getId());
            long daysSince = last == null ? Long.MAX_VALUE
                    : ChronoUnit.DAYS.between(last.toLocalDate(), today());

            if (daysSince >= lapseThresholdDays) {
                items.add(new ActionItem(s.getId(), s.getName(), "NO_BOOKING",
                        last == null ? "No bookings on record" : "No booking in " + daysSince + " days",
                        last == null ? null : last.toLocalDate(), (int) Math.min(daysSince, Integer.MAX_VALUE)));
            }

            int cancels = cancellationsThisMonth.getOrDefault(s.getId(), 0);
            if (cancels >= 3) {
                items.add(new ActionItem(s.getId(), s.getName(), "CANCELLATIONS",
                        cancels + " cancellations this month", null, cancels));
            }

            // One alert from the student's latest active membership balance ("can't book at 0").
            Integer balance = latestBalanceByStudent.get(s.getId());
            if (balance != null) {
                if (balance <= 0) {
                    items.add(new ActionItem(s.getId(), s.getName(), "MEMBERSHIP_EMPTY",
                            "Membership empty — 0 sessions left (can't book)", null,
                            SEVERITY_MEMBERSHIP_EMPTY));
                } else if (balance <= membershipLowThreshold) {
                    items.add(new ActionItem(s.getId(), s.getName(), "MEMBERSHIP_LOW",
                            "Membership low — " + balance + (balance == 1 ? " session" : " sessions") + " left",
                            null, SEVERITY_MEMBERSHIP_LOW));
                }
            }
        }

        return items.stream()
                .sorted(Comparator.comparingInt(ActionItem::severity).reversed())
                .toList();
    }

    /**
     * Unpaid invoices for the overview page's cash-flow section, oldest-issued first (the
     * most overdue is the most actionable). {@code limit} caps how many rows are shown.
     */
    public List<PendingInvoice> pendingInvoices(int limit) {
        return unpaidInvoices().stream()
                .sorted(Comparator.comparing(Invoice::getIssuedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(limit)
                .map(inv -> new PendingInvoice(
                        inv.getId(),
                        inv.getStudent() != null ? inv.getStudent().getName() : "Unlinked client",
                        inv.getNumber(),
                        inv.getAmount(),
                        inv.getCurrency(),
                        inv.getIssuedAt()))
                .toList();
    }

    /** Count and total of every unpaid invoice, for the overview stat card. */
    public PendingInvoicesSummary pendingInvoicesSummary() {
        List<Invoice> unpaid = unpaidInvoices();
        BigDecimal total = unpaid.stream()
                .map(Invoice::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PendingInvoicesSummary(unpaid.size(), total);
    }

    private List<Invoice> unpaidInvoices() {
        // findActiveWithStudent join-fetches the (optional) student so pendingInvoices() can read
        // its name outside an open session (open-in-view is off).
        return invoiceRepo.findActiveWithStudent().stream().filter(i -> !i.isPaid()).toList();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private List<Booking> activeBetween(LocalDate fromInclusive, LocalDate toExclusive) {
        return bookingRepo.findActiveWithRefsBetween(
                fromInclusive.atStartOfDay(), toExclusive.atStartOfDay());
    }

    private boolean isCounted(Booking b) {
        return !isCancelled(b);
    }

    private boolean isCancelled(Booking b) {
        return "cancelled".equalsIgnoreCase(b.getStatus());
    }

    /**
     * True when the booking's service satisfies every set dimension of {@code scope} — its location
     * (if the scope sets one) and its category (if the scope sets one), both case-insensitive. A
     * null/empty scope matches everything. The service is join-fetched by {@code activeBetween}, so
     * reading its attributes is safe with open-in-view off.
     */
    private boolean matches(Booking b, ServiceScope scope) {
        if (isAll(scope)) return true;
        ca.vicilearning.dashboard.domain.Service sv = b.getService();
        if (sv == null) return false;
        if (!isBlank(scope.location()) && !scope.location().equalsIgnoreCase(sv.getLocation())) return false;
        if (!isBlank(scope.category()) && !scope.category().equalsIgnoreCase(sv.getCategory())) return false;
        return true;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Hours from the booking's actual duration, falling back to the service's configured length. */
    private double hoursOf(Booking b) {
        long minutes;
        if (b.getEndTime() != null) {
            minutes = Duration.between(b.getStartTime(), b.getEndTime()).toMinutes();
        } else if (b.getService() != null && b.getService().getDurationMinutes() != null) {
            minutes = b.getService().getDurationMinutes();
        } else {
            minutes = 60; // last-resort default for malformed data
        }
        return minutes / 60.0;
    }

    private LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private LocalDate weekStart(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Start of the bucket containing {@code d}, for the given granularity. */
    private LocalDate bucketStart(PeriodUnit unit, LocalDate d) {
        return switch (unit) {
            case WEEK -> weekStart(d);
            case MONTH -> d.withDayOfMonth(1);
            case YEAR -> d.withDayOfYear(1);
        };
    }

    /** {@code bucketStart} shifted by {@code amount} buckets (negative steps backward). */
    private LocalDate stepBucket(PeriodUnit unit, LocalDate bucketStart, long amount) {
        return switch (unit) {
            case WEEK -> bucketStart.plusWeeks(amount);
            case MONTH -> bucketStart.plusMonths(amount);
            case YEAR -> bucketStart.plusYears(amount);
        };
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    // ── DTOs (records carried to the view) ───────────────────────────────────────

    /**
     * A page-wide service filter: an optional {@code location} (At Home / Virtual / Centre / Study
     * Clubs) and/or an optional {@code category} (Private 1:1 / Study Club / Assessment). Either or
     * both may be set; a booking matches only if it satisfies every set dimension (AND). Blank
     * fields mean "any". (Meeting #4 established location and category as two distinct axes.)
     */
    public record ServiceScope(String location, String category) {
        public boolean isAll() { return isBlank(location) && isBlank(category); }
    }

    public record Overview(long activeStudents, int sessionsThisWeek,
                           double hoursThisWeek, int cancellationsThisMonth) {}

    public record WeeklyHours(LocalDate weekStart, double hours, int sessions) {}

    /** Chart/table bucket granularity for the client's week/month/year filter. */
    public enum PeriodUnit { WEEK, MONTH, YEAR }

    public record PeriodHours(LocalDate periodStart, double hours, int sessions) {}

    public record RangeHours(double hours, int sessions) {}

    public record UpcomingSession(String studentName, String tutorName, String serviceName,
                                  LocalDateTime startTime, String status) {}

    public record TutorHours(String tutorName, double hours, int sessions) {}

    public record StudentRow(Long id, String name, String accountId, String extId, String email, String phone,
                             int sessionsThisWeek, double hoursThisWeek, StudentStatus status) {}

    /**
     * A family: the students (siblings) sharing one Account_ID, with this week's combined totals
     * plus the compact distinct lists that mirror Sara's join sheet (Meeting #3): the service
     * {@code categories} and {@code locations} seen across the family's bookings, and each
     * membership's remaining prepaid-session {@code balances}.
     */
    public record FamilyGroup(String accountId, List<FamilyMember> members,
                              int sessionsThisWeek, double hoursThisWeek,
                              List<String> categories, List<String> locations,
                              List<Integer> membershipBalances) {
        public int size() { return members.size(); }
    }

    public record FamilyMember(Long id, String name, String extId, String email, String phone,
                               int sessionsThisWeek, double hoursThisWeek) {}

    public record ActionItem(Long studentId, String studentName, String type, String reason,
                              LocalDate lastSession, int severity) {}

    public record PendingInvoice(Long id, String studentName, String number, BigDecimal amount,
                                  String currency, LocalDateTime issuedAt) {}

    public record PendingInvoicesSummary(int count, BigDecimal totalAmount) {}
}