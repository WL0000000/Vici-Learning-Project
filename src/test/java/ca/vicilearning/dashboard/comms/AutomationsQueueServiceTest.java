package ca.vicilearning.dashboard.comms;

import ca.vicilearning.dashboard.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutomationsQueueServiceTest {

    @Mock MembershipRepository membershipRepo;
    @Mock InvoiceRepository invoiceRepo;
    @Mock BookingRepository bookingRepo;
    AutomationsQueueService service;

    @BeforeEach
    void setUp() {
        service = new AutomationsQueueService(membershipRepo, invoiceRepo, bookingRepo, 2);
    }

    private Student makeStudent(long id, String name, String email) {
        Student s = new Student();
        s.setId(id);
        s.setName(name);
        s.setEmail(email);
        return s;
    }

    private Membership makeMembership(long id, Student student, Integer remaining, Boolean unlimited) {
        Membership m = new Membership();
        m.setId(id);
        m.setStudent(student);
        m.setActive(true);
        m.setRemainingCount(remaining);
        m.setUnlimited(unlimited);
        m.setInvoiceNumber("SI-" + id);
        return m;
    }

    private Invoice makeInvoice(long id, Student student, boolean paid) {
        Invoice inv = new Invoice();
        inv.setId(id);
        inv.setStudent(student);
        inv.setPaymentReceived(paid);
        inv.setAmount(new BigDecimal("100.00"));
        inv.setCurrency("CAD");
        inv.setNumber("INV-" + id);
        return inv;
    }

    private Booking makeBooking(String status, LocalDateTime startTime) {
        Booking b = new Booking();
        b.setStatus(status);
        b.setStartTime(startTime);
        return b;
    }

    // ── renewalQueue ─────────────────────────────────────────────────────────

    @Test
    void renewalQueue_includesLowBalanceActiveMembership() {
        Student s = makeStudent(1L, "Sara Kim", "sara@example.com");
        Membership m = makeMembership(10L, s, 1, false);

        when(membershipRepo.findRunningLowWithStudent(2)).thenReturn(List.of(m));

        List<AutomationsQueueService.RenewalTask> tasks = service.renewalQueue();

        assertThat(tasks).hasSize(1);
        AutomationsQueueService.RenewalTask task = tasks.get(0);
        assertThat(task.membershipId()).isEqualTo(10L);
        assertThat(task.name()).isEqualTo("Sara Kim");
        assertThat(task.email()).isEqualTo("sara@example.com");
        assertThat(task.remainingCount()).isEqualTo(1);
        assertThat(task.empty()).isFalse();
        assertThat(task.invoiceNumber()).isEqualTo("SI-10");
    }

    @Test
    void renewalQueue_flagsZeroBalanceAsEmpty() {
        Student s = makeStudent(2L, "John Park", "john@example.com");
        Membership m = makeMembership(11L, s, 0, false);

        when(membershipRepo.findRunningLowWithStudent(2)).thenReturn(List.of(m));

        List<AutomationsQueueService.RenewalTask> tasks = service.renewalQueue();

        assertThat(tasks.get(0).empty()).isTrue();
    }

    @Test
    void renewalQueue_excludesUnlimitedMemberships() {
        Student s = makeStudent(3L, "Amy Chen", "amy@example.com");
        Membership m = makeMembership(12L, s, 1, true);

        when(membershipRepo.findRunningLowWithStudent(2)).thenReturn(List.of(m));

        assertThat(service.renewalQueue()).isEmpty();
    }

    @Test
    void renewalQueue_excludesMembershipsRemindedWithinCooldown() {
        Student s = makeStudent(4L, "Mike Davis", "mike@example.com");
        Membership m = makeMembership(13L, s, 1, false);
        m.setLastRenewalReminderSentAt(LocalDateTime.now().minusDays(2)); // within the 7-day cooldown

        when(membershipRepo.findRunningLowWithStudent(2)).thenReturn(List.of(m));

        assertThat(service.renewalQueue()).isEmpty();
    }

    @Test
    void renewalQueue_reincludesMembershipAfterCooldownExpires() {
        Student s = makeStudent(5L, "Lisa Wong", "lisa@example.com");
        Membership m = makeMembership(14L, s, 1, false);
        m.setLastRenewalReminderSentAt(LocalDateTime.now().minusDays(10)); // past the 7-day cooldown

        when(membershipRepo.findRunningLowWithStudent(2)).thenReturn(List.of(m));

        assertThat(service.renewalQueue()).hasSize(1);
    }

    // ── paymentReminderQueue ─────────────────────────────────────────────────

    @Test
    void paymentReminderQueue_skipsPaidInvoices() {
        Student s = makeStudent(1L, "Sara Kim", "sara@example.com");
        Invoice inv = makeInvoice(20L, s, true);

        when(invoiceRepo.findActiveWithStudent()).thenReturn(List.of(inv));

        assertThat(service.paymentReminderQueue()).isEmpty();
        verifyNoInteractions(bookingRepo);
    }

    @Test
    void paymentReminderQueue_skipsWhenNoUpcomingSession() {
        Student s = makeStudent(2L, "John Park", "john@example.com");
        Invoice inv = makeInvoice(21L, s, false);

        when(invoiceRepo.findActiveWithStudent()).thenReturn(List.of(inv));
        when(bookingRepo.findByStudentId(2L)).thenReturn(List.of(
                makeBooking("confirmed", LocalDateTime.now().minusDays(3)))); // only a past booking

        assertThat(service.paymentReminderQueue()).isEmpty();
    }

    @Test
    void paymentReminderQueue_skipsSessionBeyondTwoWeeks() {
        Student s = makeStudent(3L, "Amy Chen", "amy@example.com");
        Invoice inv = makeInvoice(22L, s, false);

        when(invoiceRepo.findActiveWithStudent()).thenReturn(List.of(inv));
        when(bookingRepo.findByStudentId(3L)).thenReturn(List.of(
                makeBooking("confirmed", LocalDateTime.now().plusDays(20))));

        assertThat(service.paymentReminderQueue()).isEmpty();
    }

    @Test
    void paymentReminderQueue_classifiesUrgencyTiersCorrectly() {
        Student twoWeek = makeStudent(4L, "Two Week", "a@example.com");
        Invoice invTwoWeek = makeInvoice(23L, twoWeek, false);
        Student seventyTwoHour = makeStudent(5L, "Seventy Two", "b@example.com");
        Invoice inv72h = makeInvoice(24L, seventyTwoHour, false);
        Student twelveHour = makeStudent(6L, "Twelve Hour", "c@example.com");
        Invoice inv12h = makeInvoice(25L, twelveHour, false);

        when(invoiceRepo.findActiveWithStudent()).thenReturn(List.of(invTwoWeek, inv72h, inv12h));
        when(bookingRepo.findByStudentId(4L)).thenReturn(List.of(
                makeBooking("confirmed", LocalDateTime.now().plusDays(10))));
        when(bookingRepo.findByStudentId(5L)).thenReturn(List.of(
                makeBooking("confirmed", LocalDateTime.now().plusHours(48))));
        when(bookingRepo.findByStudentId(6L)).thenReturn(List.of(
                makeBooking("confirmed", LocalDateTime.now().plusHours(6))));

        List<AutomationsQueueService.PaymentReminderTask> tasks = service.paymentReminderQueue();

        assertThat(tasks).hasSize(3);
        assertThat(tasks).filteredOn(t -> t.studentId() == 4L)
                .extracting(AutomationsQueueService.PaymentReminderTask::urgency)
                .containsExactly(AutomationsQueueService.Urgency.REMINDER_2WK);
        assertThat(tasks).filteredOn(t -> t.studentId() == 5L)
                .extracting(AutomationsQueueService.PaymentReminderTask::urgency)
                .containsExactly(AutomationsQueueService.Urgency.REMINDER_72H);
        assertThat(tasks).filteredOn(t -> t.studentId() == 6L)
                .extracting(AutomationsQueueService.PaymentReminderTask::urgency)
                .containsExactly(AutomationsQueueService.Urgency.URGENT_12H);
    }

    @Test
    void paymentReminderQueue_skipsAlreadySentTierButAllowsEscalation() {
        Student s = makeStudent(7L, "Already Reminded", "d@example.com");
        Invoice inv = makeInvoice(26L, s, false);
        inv.setLastReminderTier("REMINDER_72H");

        when(invoiceRepo.findActiveWithStudent()).thenReturn(List.of(inv));
        // still 72h out and that tier already went out, so this should be skipped
        when(bookingRepo.findByStudentId(7L)).thenReturn(List.of(
                makeBooking("confirmed", LocalDateTime.now().plusHours(48))));

        assertThat(service.paymentReminderQueue()).isEmpty();
    }

    @Test
    void paymentReminderQueue_escalatesToMoreUrgentTierEvenIfEarlierOneWasSent() {
        Student s = makeStudent(8L, "Escalating", "e@example.com");
        Invoice inv = makeInvoice(27L, s, false);
        inv.setLastReminderTier("REMINDER_72H"); // already sent the 72h reminder

        when(invoiceRepo.findActiveWithStudent()).thenReturn(List.of(inv));
        // now within the 12h window, a different and more urgent tier, so this should go out
        when(bookingRepo.findByStudentId(8L)).thenReturn(List.of(
                makeBooking("confirmed", LocalDateTime.now().plusHours(6))));

        List<AutomationsQueueService.PaymentReminderTask> tasks = service.paymentReminderQueue();

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).urgency()).isEqualTo(AutomationsQueueService.Urgency.URGENT_12H);
    }

    @Test
    void paymentReminderQueue_cancelledAndDeletedBookingsAreIgnored() {
        Student s = makeStudent(9L, "Cancelled Only", "f@example.com");
        Invoice inv = makeInvoice(28L, s, false);

        Booking cancelled = makeBooking("cancelled", LocalDateTime.now().plusHours(6));
        Booking deleted = makeBooking("confirmed", LocalDateTime.now().plusHours(4));
        deleted.setDeletedAt(LocalDateTime.now());

        when(invoiceRepo.findActiveWithStudent()).thenReturn(List.of(inv));
        when(bookingRepo.findByStudentId(9L)).thenReturn(List.of(cancelled, deleted));

        assertThat(service.paymentReminderQueue()).isEmpty();
    }

    // ── mark-sent methods ────────────────────────────────────────────────────

    @Test
    void markRenewalReminderSent_stampsTheMembership() {
        Membership m = makeMembership(30L, makeStudent(1L, "Sara Kim", "sara@example.com"), 1, false);
        when(membershipRepo.findById(30L)).thenReturn(Optional.of(m));

        service.markRenewalReminderSent(30L);

        assertThat(m.getLastRenewalReminderSentAt()).isNotNull();
        verify(membershipRepo).save(m);
    }

    @Test
    void markPaymentReminderSent_stampsTheInvoiceWithTier() {
        Invoice inv = makeInvoice(31L, makeStudent(1L, "Sara Kim", "sara@example.com"), false);
        when(invoiceRepo.findById(31L)).thenReturn(Optional.of(inv));

        service.markPaymentReminderSent(31L, AutomationsQueueService.Urgency.URGENT_12H);

        assertThat(inv.getLastReminderTier()).isEqualTo("URGENT_12H");
        assertThat(inv.getLastReminderSentAt()).isNotNull();
        verify(invoiceRepo).save(inv);
    }
}
