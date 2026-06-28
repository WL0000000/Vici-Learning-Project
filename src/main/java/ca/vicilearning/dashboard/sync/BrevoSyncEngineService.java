package ca.vicilearning.dashboard.sync;

import ca.vicilearning.dashboard.comms.BrevoCommunicationService;
import ca.vicilearning.dashboard.domain.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class BrevoSyncEngineService {

    private final StudentRepository studentRepository;
    private final BookingRepository bookingRepository;
    private final AlertStudentRepository alertStudentRepository;
    private final BrevoCommunicationService brevoCommunicationService;

    public BrevoSyncEngineService(StudentRepository studentRepository,
                                  BookingRepository bookingRepository,
                                  AlertStudentRepository alertStudentRepository,
                                  BrevoCommunicationService brevoCommunicationService) {
        this.studentRepository = studentRepository;
        this.bookingRepository = bookingRepository;
        this.alertStudentRepository = alertStudentRepository;
        this.brevoCommunicationService = brevoCommunicationService;
    }

    /**
     * The Master Scanner: Scheduled to fire automatically on the hour at every :00.
     * Can also be invoked manually from an administrative controller endpoint thread.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void runTwoWayReconciliationSync() {
        System.out.println("CRON EXECUTION: Commencing hourly Brevo / Database state reconciliation sweep...");
        
        // Sweep all active students stored in the database
        List<Student> allStudents = studentRepository.findByDeletedAtIsNull();
        LocalDateTime thresholdDateTime = LocalDateTime.now().minusDays(14);

        for (Student student : allStudents) {
            String studentName = student.getName();
            String accountId = student.getAccountId();
            String parentEmail = student.getEmail();

            if (studentName == null || accountId == null) continue;

            // 1. CALCULATE LAPSED_NOW (Database Calendar State Reality)
            List<Booking> historicalBookings = bookingRepository.findByStudentId(student.getId());
            
            boolean hasRecentConfirmedBooking = false;
            boolean hasUpcomingConfirmedBooking = false;

            for (Booking booking : historicalBookings) {
                // Ignore soft-deleted logs or cancellations
                if (booking.getDeletedAt() != null || "cancelled".equalsIgnoreCase(booking.getStatus())) {
                    continue;
                }

                LocalDateTime sessionStart = booking.getStartTime();
                if (sessionStart != null) {
                    if (sessionStart.isAfter(thresholdDateTime) && sessionStart.isBefore(LocalDateTime.now())) {
                        hasRecentConfirmedBooking = true;
                    }
                    if (sessionStart.isAfter(LocalDateTime.now())) {
                        hasUpcomingConfirmedBooking = true;
                    }
                }
            }

            // A student is considered "lapsed now" if they have no session within 14 days AND no future safety block
            boolean lapsedNow = !hasRecentConfirmedBooking && !hasUpcomingConfirmedBooking;

            // 2. FETCH LAPSED_STATUS (Brevo CRM Reality State)
            boolean lapsedStatus = false; // Default baseline state if contact isn't found or parsed yet
            Map<String, Object> brevoAttributes = brevoCommunicationService.getAttributesByAccountId(accountId);

            if (brevoAttributes != null) {
                String namesArrayRaw = (String) brevoAttributes.get("STUDENT_NAMES");
                String statusArrayRaw = (String) brevoAttributes.get("STUDENT_STATUS");

                if (namesArrayRaw != null && statusArrayRaw != null) {
                    // Split the parallel text segments mapping to find this child's positional index
                    String[] names = namesArrayRaw.split("\\s*\\|\\s*");
                    String[] statuses = statusArrayRaw.split("\\s*\\|\\s*");

                    for (int i = 0; i < names.length; i++) {
                        if (studentName.equalsIgnoreCase(names[i]) && i < statuses.length) {
                            if ("LAPSED".equalsIgnoreCase(statuses[i])) {
                                lapsedStatus = true;
                            }
                            break;
                        }
                    }
                }
            }

            // 3. RECONCILE AND DROP/UPDATE snapshot state metrics into your ledger table
            AlertStudent alertRow = new AlertStudent(
                studentName,
                accountId,
                parentEmail,
                lapsedNow,
                lapsedStatus,
                LocalDateTime.now()
            );
            alertStudentRepository.save(alertRow);
        }
        System.out.println("CRON SUCCESS: Multi-source state map committed into table ledger.");
    }
}