package ca.vicilearning.dashboard.metrics;

import ca.vicilearning.dashboard.domain.Booking;
import ca.vicilearning.dashboard.domain.BookingRepository;
import ca.vicilearning.dashboard.domain.Student;
import ca.vicilearning.dashboard.domain.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardMetricsServiceTest {

    @Mock BookingRepository bookingRepo;
    @Mock StudentRepository studentRepo;
    @InjectMocks DashboardMetricsService service;

    private final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

    @Test
    void overview_countsSessionsAndHours_excludingCancelled() {
        // 1h + 2h confirmed = 3h over 2 sessions; the cancelled one is excluded from both.
        when(bookingRepo.findActiveWithRefsBetween(any(), any())).thenReturn(List.of(
                booking(1, null, "confirmed", now, 60),
                booking(2, null, "confirmed", now, 120),
                booking(3, null, "cancelled", now, 60)));
        when(studentRepo.countByDeletedAtIsNull()).thenReturn(42L);

        DashboardMetricsService.Overview o = service.overview();

        assertThat(o.activeStudents()).isEqualTo(42L);
        assertThat(o.sessionsThisWeek()).isEqualTo(2);
        assertThat(o.hoursThisWeek()).isEqualTo(3.0);
        // Same stubbed list is used for the month query → one cancelled booking.
        assertThat(o.cancellationsThisMonth()).isEqualTo(1);
    }

    @Test
    void weeklyHours_bucketsCurrentWeek_andExcludesCancelled() {
        when(bookingRepo.findActiveWithRefsBetween(any(), any())).thenReturn(List.of(
                booking(1, null, "confirmed", now, 60),
                booking(2, null, "confirmed", now, 120),
                booking(3, null, "cancelled", now, 60)));

        // Just the current week.
        List<DashboardMetricsService.WeeklyHours> weeks = service.weeklyHours(0, 0);

        assertThat(weeks).hasSize(1);
        assertThat(weeks.get(0).hours()).isEqualTo(3.0);
        assertThat(weeks.get(0).sessions()).isEqualTo(2);
    }

    @Test
    void studentRows_joinStudentsWithThisWeeksHours() {
        Student alice = student(1L, "Alice");
        Student bob = student(2L, "Bob");
        when(studentRepo.findByDeletedAtIsNull()).thenReturn(List.of(bob, alice));
        // Only Alice has a booking this week (a 2h session).
        when(bookingRepo.findActiveWithRefsBetween(any(), any()))
                .thenReturn(List.of(booking(1, alice, "confirmed", now, 120)));

        List<DashboardMetricsService.StudentRow> rows = service.studentRows();

        // Sorted by name → Alice first.
        assertThat(rows).extracting(DashboardMetricsService.StudentRow::name)
                .containsExactly("Alice", "Bob");
        assertThat(rows.get(0).hoursThisWeek()).isEqualTo(2.0);
        assertThat(rows.get(0).sessionsThisWeek()).isEqualTo(1);
        assertThat(rows.get(1).hoursThisWeek()).isEqualTo(0.0);
        assertThat(rows.get(0).accountId()).isEqualTo("VICI-0001");
    }

    private Booking booking(long id, Student student, String status, LocalDateTime start, int minutes) {
        Booking b = new Booking();
        b.setId(id);
        b.setStudent(student);
        b.setStatus(status);
        b.setStartTime(start);
        b.setEndTime(start.plusMinutes(minutes));
        return b;
    }

    private Student student(long id, String name) {
        Student s = new Student();
        s.setId(id);
        s.setName(name);
        s.setAccountId(String.format("VICI-%04d", id));
        return s;
    }
}
