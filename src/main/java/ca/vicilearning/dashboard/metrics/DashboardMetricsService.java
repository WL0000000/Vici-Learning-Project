package ca.vicilearning.dashboard.metrics;

import ca.vicilearning.dashboard.domain.Booking;
import ca.vicilearning.dashboard.domain.BookingRepository;
import ca.vicilearning.dashboard.domain.Invoice;
import ca.vicilearning.dashboard.domain.InvoiceRepository;
import ca.vicilearning.dashboard.domain.Student;
import ca.vicilearning.dashboard.domain.StudentRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    public DashboardMetricsService(BookingRepository bookingRepo, StudentRepository studentRepo,
                                    InvoiceRepository invoiceRepo) {
        this.bookingRepo = bookingRepo;
        this.studentRepo = studentRepo;
        this.invoiceRepo = invoiceRepo;
    }

    // ── Public metrics ─────────────────────────────────────────────────────────

    /** Headline overview cards for the current week / month. */
    public Overview overview() {
        LocalDate today = today();
        LocalDate weekStart = weekStart(today);

        List<Booking> thisWeek = activeBetween(weekStart, weekStart.plusWeeks(1));
        int sessions = (int) thisWeek.stream().filter(this::isCounted).count();
        double hours = thisWeek.stream().filter(this::isCounted).mapToDouble(this::hoursOf).sum();

        LocalDate monthStart = today.withDayOfMonth(1);
        long cancellations = activeBetween(monthStart, monthStart.plusMonths(1)).stream()
                .filter(this::isCancelled).count();

        return new Overview(
                studentRepo.countByDeletedAtIsNull(),
                sessions,
                round1(hours),
                (int) cancellations);
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
        LocalDate currentBucket = bucketStart(unit, today());
        LocalDate firstBucket = stepBucket(unit, currentBucket, -periodsBack);
        LocalDate endExclusive = stepBucket(unit, currentBucket, periodsAhead + 1L);

        // Seed every bucket with zeroes so gaps render as 0 rather than disappearing.
        Map<LocalDate, double[]> byBucket = new LinkedHashMap<>(); // [hours, sessions]
        for (LocalDate b = firstBucket; b.isBefore(endExclusive); b = stepBucket(unit, b, 1)) {
            byBucket.put(b, new double[]{0.0, 0.0});
        }

        for (Booking bk : activeBetween(firstBucket, endExclusive)) {
            if (!isCounted(bk)) continue;
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
        List<Booking> bookings = activeBetween(fromInclusive, toExclusive).stream()
                .filter(this::isCounted)
                .toList();
        double hours = bookings.stream().mapToDouble(this::hoursOf).sum();
        return new RangeHours(round1(hours), bookings.size());
    }

    /** Next {@code limit} upcoming (non-cancelled) sessions, soonest first. */
    public List<UpcomingSession> upcoming(int limit) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return bookingRepo.findActiveWithRefsBetween(now, now.plusDays(60)).stream()
                .filter(this::isCounted)
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
        LocalDate start = bucketStart(unit, today());
        return tutorHoursForRange(start, stepBucket(unit, start, 1), sortByName);
    }

    /**
     * Per-tutor hours/sessions over an arbitrary date range. Sorted by total hours descending
     * by default (her "sort by total"), or alphabetically when {@code sortByName} is set
     * (her "sort by tutor").
     */
    public List<TutorHours> tutorHoursForRange(LocalDate fromInclusive, LocalDate toExclusive, boolean sortByName) {
        Map<String, double[]> byTutor = new LinkedHashMap<>();
        for (Booking b : activeBetween(fromInclusive, toExclusive)) {
            if (!isCounted(b)) continue;
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
        Map<Long, double[]> byStudent = hoursThisWeekByStudent();

        return studentRepo.findByDeletedAtIsNull().stream()
                .sorted(Comparator.comparing(Student::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(s -> {
                    double[] cell = byStudent.getOrDefault(s.getId(), new double[]{0.0, 0.0});
                    return new StudentRow(
                            s.getId(), s.getName(), s.getAccountId(), s.getEmail(), s.getPhone(),
                            (int) cell[1], round1(cell[0]));
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
        Map<Long, double[]> byStudent = hoursThisWeekByStudent();

        // Preserve insertion order per key so members stay sorted by the pre-sorted stream below.
        Map<String, List<FamilyMember>> byAccount = new LinkedHashMap<>();
        studentRepo.findByDeletedAtIsNull().stream()
                .filter(s -> s.getAccountId() != null && !s.getAccountId().isBlank())
                .sorted(Comparator.comparing(Student::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .forEach(s -> {
                    double[] cell = byStudent.getOrDefault(s.getId(), new double[]{0.0, 0.0});
                    byAccount.computeIfAbsent(s.getAccountId(), k -> new ArrayList<>())
                            .add(new FamilyMember(s.getId(), s.getName(), s.getEmail(), s.getPhone(),
                                    (int) cell[1], round1(cell[0])));
                });

        return byAccount.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(e -> {
                    List<FamilyMember> members = e.getValue();
                    int sessions = members.stream().mapToInt(FamilyMember::sessionsThisWeek).sum();
                    double hours = members.stream().mapToDouble(FamilyMember::hoursThisWeek).sum();
                    return new FamilyGroup(e.getKey(), members, sessions, round1(hours));
                })
                .toList();
    }

    /** This week's booked [hours, sessions] per active student id, cancellations excluded. */
    private Map<Long, double[]> hoursThisWeekByStudent() {
        LocalDate weekStart = weekStart(today());
        Map<Long, double[]> byStudent = new LinkedHashMap<>(); // [hours, sessions]
        for (Booking b : activeBetween(weekStart, weekStart.plusWeeks(1))) {
            if (!isCounted(b)) continue;
            double[] cell = byStudent.computeIfAbsent(b.getStudent().getId(), k -> new double[]{0.0, 0.0});
            cell[0] += hoursOf(b);
            cell[1] += 1;
        }
        return byStudent;
    }

    /**
     * Students needing attention, getting this from real booking history:
     *  - no booking in 21+ days (using their most recent active booking)
     *  - 3+ cancellations this calendar month
     * Sorted worst-first (longest gap / most cancellations first).
     */
    public List<ActionItem> actionRequired() {
        LocalDate monthStart = today().withDayOfMonth(1);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        Map<Long, LocalDateTime> lastBookingByStudent = new LinkedHashMap<>();
        Map<Long, Integer> cancellationsThisMonth = new LinkedHashMap<>();

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

            if (daysSince >= 21) {
                items.add(new ActionItem(s.getId(), s.getName(), "NO_BOOKING",
                        last == null ? "No bookings on record" : "No booking in " + daysSince + " days",
                        last == null ? null : last.toLocalDate(), (int) Math.min(daysSince, Integer.MAX_VALUE)));
            }

            int cancels = cancellationsThisMonth.getOrDefault(s.getId(), 0);
            if (cancels >= 3) {
                items.add(new ActionItem(s.getId(), s.getName(), "CANCELLATIONS",
                        cancels + " cancellations this month", null, cancels));
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

    public record StudentRow(Long id, String name, String accountId, String email, String phone,
                             int sessionsThisWeek, double hoursThisWeek) {}

    /** A family: the students (siblings) sharing one Account_ID, with this week's combined totals. */
    public record FamilyGroup(String accountId, List<FamilyMember> members,
                              int sessionsThisWeek, double hoursThisWeek) {
        public int size() { return members.size(); }
    }

    public record FamilyMember(Long id, String name, String email, String phone,
                               int sessionsThisWeek, double hoursThisWeek) {}

    public record ActionItem(Long studentId, String studentName, String type, String reason,
                              LocalDate lastSession, int severity) {}

    public record PendingInvoice(Long id, String studentName, String number, BigDecimal amount,
                                  String currency, LocalDateTime issuedAt) {}

    public record PendingInvoicesSummary(int count, BigDecimal totalAmount) {}
}