package ca.vicilearning.dashboard.comms;

import ca.vicilearning.dashboard.domain.AppClock;
import ca.vicilearning.dashboard.domain.Booking;
import ca.vicilearning.dashboard.domain.BookingRepository;
import ca.vicilearning.dashboard.domain.Invoice;
import ca.vicilearning.dashboard.domain.InvoiceRepository;
import ca.vicilearning.dashboard.domain.Membership;
import ca.vicilearning.dashboard.domain.MembershipRepository;
import ca.vicilearning.dashboard.domain.Student;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Builds the two follow-up queues for the Automations page. Renewal queue is families running
// low on their prepaid session pack (Membership.remainingCount). Payment reminder queue is
// unpaid invoices with a session coming up soon, split into 2wk/72h/12h tiers. Both just read
// data the sync already keeps up to date, nothing new to pull here.
@Service
@Transactional(readOnly = true)
public class AutomationsQueueService {

    // same threshold as DashboardMetricsService's membership-low alert
    private final int membershipLowThreshold;

    private final MembershipRepository membershipRepo;
    private final InvoiceRepository invoiceRepo;
    private final BookingRepository bookingRepo;

    public AutomationsQueueService(MembershipRepository membershipRepo,
                                   InvoiceRepository invoiceRepo,
                                   BookingRepository bookingRepo,
                                   @Value("${metrics.membership-low-threshold:2}") int membershipLowThreshold) {
        this.membershipRepo = membershipRepo;
        this.invoiceRepo = invoiceRepo;
        this.bookingRepo = bookingRepo;
        this.membershipLowThreshold = membershipLowThreshold;
    }

    // don't re-nag every time the page loads, give it a week before it resurfaces
    private static final int RENEWAL_REMINDER_COOLDOWN_DAYS = 7;

    /** Which reminder tier a payment-reminder task falls into, by hours until the next session. */
    public enum Urgency { REMINDER_2WK, REMINDER_72H, URGENT_12H }

    public record RenewalTask(Long membershipId, Long studentId, String name, String email,
                              Integer remainingCount, boolean empty, String invoiceNumber) {}

    public record PaymentReminderTask(Long invoiceId, Long studentId, String name, String email,
                                      String invoiceNumber, BigDecimal amount, String currency,
                                      LocalDate nextSessionDate, long hoursUntilSession, Urgency urgency) {}

    // one row per membership, so a family with multiple kids gets a row each. unlimited packages
    // never run out so they're skipped regardless of balance.
    public List<RenewalTask> renewalQueue() {
        LocalDateTime cutoff = LocalDateTime.now(AppClock.ZONE).minusDays(RENEWAL_REMINDER_COOLDOWN_DAYS);
        List<RenewalTask> tasks = new ArrayList<>();
        for (Membership m : membershipRepo.findRunningLowWithStudent(membershipLowThreshold)) {
            if (Boolean.TRUE.equals(m.getUnlimited()) || m.getStudent() == null) {
                continue;
            }
            if (m.getLastRenewalReminderSentAt() != null && m.getLastRenewalReminderSentAt().isAfter(cutoff)) {
                continue;
            }
            Student student = m.getStudent();
            tasks.add(new RenewalTask(
                    m.getId(), student.getId(), student.getName(), student.getEmail(),
                    m.getRemainingCount(), m.getRemainingCount() <= 0, m.getInvoiceNumber()));
        }
        return tasks;
    }

    // only invoices with a session in the next 14 days count as "imminent". no upcoming session
    // means it just belongs on the regular pending-invoices list instead. already-sent tiers get
    // skipped but a more urgent tier can still fire even if an earlier one already went out.
    public List<PaymentReminderTask> paymentReminderQueue() {
        LocalDateTime now = LocalDateTime.now(AppClock.ZONE);
        List<PaymentReminderTask> tasks = new ArrayList<>();

        for (Invoice inv : invoiceRepo.findActiveWithStudent()) {
            if (inv.isPaid() || inv.getStudent() == null) {
                continue;
            }
            Student student = inv.getStudent();
            LocalDateTime nextSession = nextUpcomingSession(student.getId(), now);
            if (nextSession == null) {
                continue;
            }
            long hoursUntil = Duration.between(now, nextSession).toHours();
            if (hoursUntil > 24 * 14) {
                continue;
            }
            Urgency urgency = hoursUntil <= 12 ? Urgency.URGENT_12H
                    : hoursUntil <= 72 ? Urgency.REMINDER_72H
                    : Urgency.REMINDER_2WK;
            if (urgency.name().equals(inv.getLastReminderTier())) {
                continue;
            }
            tasks.add(new PaymentReminderTask(
                    inv.getId(), student.getId(), student.getName(), student.getEmail(), inv.getNumber(),
                    inv.getAmount(), inv.getCurrency(), nextSession.toLocalDate(), hoursUntil, urgency));
        }
        return tasks;
    }

    @Transactional
    public void markRenewalReminderSent(Long membershipId) {
        membershipRepo.findById(membershipId).ifPresent(m -> {
            m.setLastRenewalReminderSentAt(LocalDateTime.now(AppClock.ZONE));
            membershipRepo.save(m);
        });
    }

    @Transactional
    public void markPaymentReminderSent(Long invoiceId, Urgency urgency) {
        invoiceRepo.findById(invoiceId).ifPresent(inv -> {
            inv.setLastReminderTier(urgency.name());
            inv.setLastReminderSentAt(LocalDateTime.now(AppClock.ZONE));
            invoiceRepo.save(inv);
        });
    }

    private LocalDateTime nextUpcomingSession(Long studentId, LocalDateTime now) {
        return bookingRepo.findByStudentId(studentId).stream()
                .filter(b -> b.getDeletedAt() == null && !isCancelled(b))
                .map(Booking::getStartTime)
                .filter(t -> t != null && t.isAfter(now))
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private boolean isCancelled(Booking b) {
        return "cancelled".equalsIgnoreCase(b.getStatus());
    }
}
