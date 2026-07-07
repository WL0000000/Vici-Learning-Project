package ca.vicilearning.dashboard.metrics;

import ca.vicilearning.dashboard.domain.Booking;
import ca.vicilearning.dashboard.domain.BookingRepository;
import ca.vicilearning.dashboard.domain.Invoice;
import ca.vicilearning.dashboard.domain.InvoiceRepository;
import ca.vicilearning.dashboard.domain.Student;
import ca.vicilearning.dashboard.domain.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
    @Mock InvoiceRepository invoiceRepo;
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

    @Test
    void actionRequired_flagsNoBookingGap_andCancellationPattern() {
        Student staleStudent = student(1L, "Jane Doe");
        Student healthyStudent = student(2L, "Healthy Student");
        Student cancelHeavyStudent = student(3L, "Cancel Heavy");

        when(studentRepo.findByDeletedAtIsNull())
                .thenReturn(List.of(staleStudent, healthyStudent, cancelHeavyStudent));

        when(bookingRepo.findByDeletedAtIsNull()).thenReturn(List.of(
                // last booking was 30 days ago -> should trigger NO_BOOKING
                booking(1, staleStudent, "confirmed", now.minusDays(30), 60),
                // no flag expected here
                booking(2, healthyStudent, "confirmed", now.minusDays(2), 60),
                // should trigger CANCELLATIONS
                booking(3, cancelHeavyStudent, "cancelled", now, 60),
                booking(4, cancelHeavyStudent, "cancelled", now, 60),
                booking(5, cancelHeavyStudent, "cancelled", now, 60),
                // also has a recent confirmed booking so it should NOT get flagged for no-booking
                booking(6, cancelHeavyStudent, "confirmed", now.minusDays(1), 60)));

        List<DashboardMetricsService.ActionItem> items = service.actionRequired();

        assertThat(items).extracting(DashboardMetricsService.ActionItem::studentName)
                .containsExactlyInAnyOrder("Jane Doe", "Cancel Heavy");

        DashboardMetricsService.ActionItem janeItem = items.stream()
                .filter(i -> i.studentName().equals("Jane Doe")).findFirst().orElseThrow();
        assertThat(janeItem.type()).isEqualTo("NO_BOOKING");
        assertThat(janeItem.reason()).contains("30 days");

        DashboardMetricsService.ActionItem cancelItem = items.stream()
                .filter(i -> i.studentName().equals("Cancel Heavy")).findFirst().orElseThrow();
        assertThat(cancelItem.type()).isEqualTo("CANCELLATIONS");
        assertThat(cancelItem.reason()).contains("3 cancellations");
    }

    @Test
    void actionRequired_returnsEmpty_whenNoStudentsNeedAttention() {
        Student healthyStudent = student(1L, "All Good");
        when(studentRepo.findByDeletedAtIsNull()).thenReturn(List.of(healthyStudent));
        when(bookingRepo.findByDeletedAtIsNull())
                .thenReturn(List.of(booking(1, healthyStudent, "confirmed", now.minusDays(1), 60)));

        List<DashboardMetricsService.ActionItem> items = service.actionRequired();

        assertThat(items).isEmpty();
    }

    @Test
    void pendingInvoices_excludesPaid_andSortsOldestIssuedFirst() {
        Student jane = student(1L, "Jane Doe");
        when(invoiceRepo.findByDeletedAtIsNull()).thenReturn(List.of(
                invoice(1L, jane, "paid", "50.00", now.minusDays(10)),
                invoice(2L, jane, "pending", "75.00", now.minusDays(2)),
                invoice(3L, null, "pending", "25.00", now.minusDays(20))));

        List<DashboardMetricsService.PendingInvoice> pending = service.pendingInvoices(10);

        assertThat(pending).extracting(DashboardMetricsService.PendingInvoice::id)
                .containsExactly(3L, 2L);
        assertThat(pending.get(0).studentName()).isEqualTo("Unlinked client");
    }

    @Test
    void pendingInvoicesSummary_sumsUnpaidAmounts_ignoringPaidAndNullAmounts() {
        Student jane = student(1L, "Jane Doe");
        when(invoiceRepo.findByDeletedAtIsNull()).thenReturn(List.of(
                invoice(1L, jane, "paid", "50.00", now),
                invoice(2L, jane, "pending", "75.00", now),
                invoice(3L, jane, "pending", null, now)));

        DashboardMetricsService.PendingInvoicesSummary summary = service.pendingInvoicesSummary();

        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.totalAmount()).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    private Invoice invoice(long id, Student student, String status, String amount, LocalDateTime issuedAt) {
        Invoice inv = new Invoice();
        inv.setId(id);
        inv.setStudent(student);
        inv.setStatus(status);
        inv.setAmount(amount == null ? null : new BigDecimal(amount));
        inv.setIssuedAt(issuedAt);
        return inv;
    }
}
