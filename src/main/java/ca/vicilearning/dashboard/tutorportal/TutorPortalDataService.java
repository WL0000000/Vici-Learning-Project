package ca.vicilearning.dashboard.tutorportal;

import ca.vicilearning.dashboard.domain.Booking;
import ca.vicilearning.dashboard.domain.BookingRepository;
import ca.vicilearning.dashboard.domain.Tutor;
import ca.vicilearning.dashboard.domain.TutorRepository;
import ca.vicilearning.dashboard.metrics.DashboardMetricsService.UpcomingSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Real data for the tutor portal, scoped to whichever tutor is logged in. Uses its own
 * StudentSummary record (below) instead of the shared DashboardMetricsService.StudentRow,
 * so adding tutor-portal-only fields (total sessions, consistency) never risks touching
 * a record shape the rest of the app also depends on.
 *
 * There isn't yet a proper foreign key linking a login account (AppUser) to the actual
 * SimplyBook.me tutor record (Tutor). Bridged for now by matching the login username
 * against Tutor.email (case-insensitive). If nothing matches, resolveTutor() returns
 * empty and the controllers show a "not linked yet" message instead of guessing.
 *
 * Class-level @Transactional keeps the Hibernate session open for the full duration of
 * any single method call here, so lazy fields (Booking.student, Booking.service) resolve
 * safely. That only works within ONE method call though — if a controller fetches bookings
 * in one call and converts them to UpcomingSession in a separate later call, the session
 * from the first call has already closed by the time the second one runs, causing a
 * LazyInitializationException. weekStats() exists specifically to avoid that: it fetches
 * and converts everything in a single transactional call.
 */
@Service
@Transactional(readOnly = true)
public class TutorPortalDataService {

    private final TutorRepository tutorRepo;
    private final BookingRepository bookingRepo;

    public TutorPortalDataService(TutorRepository tutorRepo, BookingRepository bookingRepo) {
        this.tutorRepo = tutorRepo;
        this.bookingRepo = bookingRepo;
    }

    public Optional<Tutor> resolveTutor(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        return tutorRepo.findByDeletedAtIsNull().stream()
                .filter(t -> t.getEmail() != null && t.getEmail().equalsIgnoreCase(username.trim()))
                .findFirst();
    }

    /** This tutor's non-cancelled bookings for the given week (Monday start, 7 days). */
    public List<Booking> weekBookings(Tutor tutor, LocalDate weekStart) {
        return rangeBookings(tutor, weekStart, weekStart.plusDays(7));
    }

    /** This tutor's non-cancelled bookings in [fromInclusive, toExclusive). */
    public List<Booking> rangeBookings(Tutor tutor, LocalDate fromInclusive, LocalDate toExclusive) {
        return bookingRepo.findByTutorId(tutor.getId()).stream()
                .filter(b -> b.getDeletedAt() == null)
                .filter(b -> !isCancelled(b))
                .filter(b -> {
                    LocalDate d = b.getStartTime().toLocalDate();
                    return !d.isBefore(fromInclusive) && d.isBefore(toExclusive);
                })
                .sorted(Comparator.comparing(Booking::getStartTime))
                .toList();
    }

    /**
     * Fetches this week's bookings and converts them to UpcomingSession within the same
     * transaction, so any lazy student/tutor/service fields resolve correctly instead of
     * throwing once the fetching transaction has closed. Use this (not weekBookings +
     * a separate map) whenever a controller needs both the stats and the session list.
     */
    public WeekStats weekStats(Tutor tutor, LocalDate weekStart) {
        List<Booking> bookings = weekBookings(tutor, weekStart);
        double hours = bookings.stream().mapToDouble(this::hoursOf).sum();
        List<UpcomingSession> sessions = bookings.stream().map(this::toUpcomingSession).toList();
        return new WeekStats(bookings.size(), round1(hours), sessions);
    }

    /** Grouped week-by-week upcoming bookings, {@code weeksAhead} weeks starting this week. Used for the 2-month schedule view Sara asked for in Meeting 5. */
    public List<WeekGroup> upcomingWeekGroups(Tutor tutor, int weeksAhead) {
        LocalDate weekStart = weekStart(today());
        List<WeekGroup> groups = new ArrayList<>();
        for (int w = 0; w < weeksAhead; w++) {
            LocalDate start = weekStart.plusWeeks(w);
            List<Booking> bookings = rangeBookings(tutor, start, start.plusDays(7));
            if (bookings.isEmpty()) continue; // skip empty weeks so the list stays compact
            List<UpcomingSession> sessions = bookings.stream().map(this::toUpcomingSession).toList();
            groups.add(new WeekGroup(weekLabel(start), sessions));
        }
        return groups;
    }

    /**
     * Distinct students this tutor currently has upcoming sessions with, along with the
     * "score" Sara asked for in Meeting 5: total historical session count with each student,
     * and a rough consistency figure (average sessions per month since the first booking).
     */
    public List<StudentSummary> myStudentSummaries(Tutor tutor) {
        LocalDate weekStart = weekStart(today());
        List<Booking> allNonCancelled = bookingRepo.findByTutorId(tutor.getId()).stream()
                .filter(b -> b.getDeletedAt() == null)
                .filter(b -> !isCancelled(b))
                .toList();

        Map<Long, double[]> thisWeekByStudent = new LinkedHashMap<>(); // [hours, sessions]
        for (Booking b : allNonCancelled) {
            LocalDate d = b.getStartTime().toLocalDate();
            if (!d.isBefore(weekStart) && d.isBefore(weekStart.plusDays(7))) {
                double[] cell = thisWeekByStudent.computeIfAbsent(b.getStudent().getId(), k -> new double[]{0.0, 0.0});
                cell[0] += hoursOf(b);
                cell[1] += 1;
            }
        }

        Map<Long, List<Booking>> byStudent = new LinkedHashMap<>();
        for (Booking b : allNonCancelled) {
            byStudent.computeIfAbsent(b.getStudent().getId(), k -> new ArrayList<>()).add(b);
        }

        // "currently booked with" = has at least one non-cancelled booking today or later
        LocalDate today = today();
        List<StudentSummary> out = new ArrayList<>();
        for (var entry : byStudent.entrySet()) {
            List<Booking> studentBookings = entry.getValue();
            boolean hasUpcoming = studentBookings.stream()
                    .anyMatch(b -> !b.getStartTime().toLocalDate().isBefore(today));
            if (!hasUpcoming) continue;

            var s = studentBookings.get(0).getStudent();
            double[] cell = thisWeekByStudent.getOrDefault(entry.getKey(), new double[]{0.0, 0.0});

            LocalDate firstSession = studentBookings.stream()
                    .map(b -> b.getStartTime().toLocalDate())
                    .min(LocalDate::compareTo)
                    .orElse(today);
            long monthsActive = Math.max(1, ChronoUnit.MONTHS.between(firstSession, today));
            double avgPerMonth = round1((double) studentBookings.size() / monthsActive);

            out.add(new StudentSummary(
                    s.getId(), s.getName(), s.getEmail(), s.getPhone(),
                    (int) cell[1], round1(cell[0]),
                    studentBookings.size(), avgPerMonth));
        }
        return out;
    }

    /** Average total (all-time) sessions per assigned student — Sara's "overall for the tutor" ask. */
    public double avgSessionsPerStudent(Tutor tutor) {
        List<StudentSummary> summaries = myStudentSummaries(tutor);
        if (summaries.isEmpty()) return 0.0;
        double totalSessions = summaries.stream().mapToInt(StudentSummary::totalSessions).sum();
        return round1(totalSessions / summaries.size());
    }

    public UpcomingSession toUpcomingSession(Booking b) {
        return new UpcomingSession(
                b.getStudent().getName(),
                b.getTutor() != null ? b.getTutor().getName() : "Unassigned",
                b.getService() != null ? b.getService().getName() : "Session",
                b.getStartTime(),
                b.getStatus());
    }

    public double hoursOf(Booking b) {
        long minutes;
        if (b.getEndTime() != null) {
            minutes = Duration.between(b.getStartTime(), b.getEndTime()).toMinutes();
        } else if (b.getService() != null && b.getService().getDurationMinutes() != null) {
            minutes = b.getService().getDurationMinutes();
        } else {
            minutes = 60;
        }
        return minutes / 60.0;
    }

    public boolean isCancelled(Booking b) {
        return "cancelled".equalsIgnoreCase(b.getStatus());
    }

    public LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    public LocalDate weekStart(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private String weekLabel(LocalDate weekStart) {
        var fmt = java.time.format.DateTimeFormatter.ofPattern("MMM d");
        return weekStart.format(fmt) + " - " + weekStart.plusDays(6).format(fmt);
    }

    /** A tutor-portal-only student view: basic contact + the session history "score" Sara wants. */
    public record StudentSummary(Long id, String name, String email, String phone,
                                  int sessionsThisWeek, double hoursThisWeek,
                                  int totalSessions, double avgSessionsPerMonth) {}

    public record WeekGroup(String weekLabel, List<UpcomingSession> sessions) {}

    /** Bundles a week's session count, hours, and the already-converted session list together, all built inside one transaction. */
    public record WeekStats(int sessionsThisWeek, double hoursThisWeek, List<UpcomingSession> sessions) {}
}