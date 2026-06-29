package ca.vicilearning.dashboard.metrics;

import ca.vicilearning.dashboard.domain.Booking;
import ca.vicilearning.dashboard.domain.BookingRepository;
import ca.vicilearning.dashboard.domain.Student;
import ca.vicilearning.dashboard.domain.StudentRepository;
import org.springframework.stereotype.Service;

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

    public DashboardMetricsService(BookingRepository bookingRepo, StudentRepository studentRepo) {
        this.bookingRepo = bookingRepo;
        this.studentRepo = studentRepo;
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
     */
    public List<WeeklyHours> weeklyHours(int weeksBack, int weeksAhead) {
        LocalDate firstWeek = weekStart(today()).minusWeeks(weeksBack);
        LocalDate endExclusive = weekStart(today()).plusWeeks(weeksAhead + 1L);

        // Seed every week with zeroes so gaps render as 0 rather than disappearing.
        Map<LocalDate, double[]> byWeek = new LinkedHashMap<>(); // [hours, sessions]
        for (LocalDate w = firstWeek; w.isBefore(endExclusive); w = w.plusWeeks(1)) {
            byWeek.put(w, new double[]{0.0, 0.0});
        }

        for (Booking b : activeBetween(firstWeek, endExclusive)) {
            if (!isCounted(b)) continue;
            LocalDate w = weekStart(b.getStartTime().toLocalDate());
            double[] cell = byWeek.get(w);
            if (cell != null) {
                cell[0] += hoursOf(b);
                cell[1] += 1;
            }
        }

        List<WeeklyHours> out = new ArrayList<>();
        byWeek.forEach((week, cell) -> out.add(new WeeklyHours(week, round1(cell[0]), (int) cell[1])));
        return out;
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

    /** Hours booked this week grouped by tutor (the "tutor drill-down" Sara called her killer feature). */
    public List<TutorHours> tutorHoursThisWeek() {
        LocalDate weekStart = weekStart(today());
        Map<String, double[]> byTutor = new LinkedHashMap<>();
        for (Booking b : activeBetween(weekStart, weekStart.plusWeeks(1))) {
            if (!isCounted(b)) continue;
            String tutor = b.getTutor() != null ? b.getTutor().getName() : "Unassigned";
            double[] cell = byTutor.computeIfAbsent(tutor, k -> new double[]{0.0, 0.0});
            cell[0] += hoursOf(b);
            cell[1] += 1;
        }
        List<TutorHours> out = new ArrayList<>();
        byTutor.forEach((tutor, cell) -> out.add(new TutorHours(tutor, round1(cell[0]), (int) cell[1])));
        out.sort(Comparator.comparingDouble(TutorHours::hours).reversed());
        return out;
    }

    /**
     * One row per active student with this week's booked sessions/hours — the spreadsheet
     * Brevo+SimplyBook join made live: identity + Account_ID alongside computed weekly hours.
     */
    public List<StudentRow> studentRows() {
        LocalDate weekStart = weekStart(today());

        Map<Long, double[]> byStudent = new LinkedHashMap<>(); // [hours, sessions]
        for (Booking b : activeBetween(weekStart, weekStart.plusWeeks(1))) {
            if (!isCounted(b)) continue;
            double[] cell = byStudent.computeIfAbsent(b.getStudent().getId(), k -> new double[]{0.0, 0.0});
            cell[0] += hoursOf(b);
            cell[1] += 1;
        }

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

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    // ── DTOs (records carried to the view) ───────────────────────────────────────

    public record Overview(long activeStudents, int sessionsThisWeek,
                           double hoursThisWeek, int cancellationsThisMonth) {}

    public record WeeklyHours(LocalDate weekStart, double hours, int sessions) {}

    public record UpcomingSession(String studentName, String tutorName, String serviceName,
                                  LocalDateTime startTime, String status) {}

    public record TutorHours(String tutorName, double hours, int sessions) {}

    public record StudentRow(Long id, String name, String accountId, String email, String phone,
                             int sessionsThisWeek, double hoursThisWeek) {}

    public record ActionItem(Long studentId, String studentName, String type, String reason,
                              LocalDate lastSession, int severity) {}
}