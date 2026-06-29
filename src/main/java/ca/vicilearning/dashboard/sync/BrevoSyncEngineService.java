package ca.vicilearning.dashboard.sync;

import ca.vicilearning.dashboard.comms.BrevoCommunicationService;
import ca.vicilearning.dashboard.domain.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class BrevoSyncEngineService {

    private final StudentRepository studentRepository;
    private final BookingRepository bookingRepository;
    private final AlertStudentRepository alertStudentRepository;
    private final BrevoCommunicationService communicationService;

    public BrevoSyncEngineService(StudentRepository studentRepository,
                                  BookingRepository bookingRepository,
                                  AlertStudentRepository alertStudentRepository,
                                  BrevoCommunicationService communicationService) {
        this.studentRepository = studentRepository;
        this.bookingRepository = bookingRepository;
        this.alertStudentRepository = alertStudentRepository;
        this.communicationService = communicationService;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void runTwoWayReconciliationSync() {
        System.out.println("\n=== [CRON BACKGROUND SYNC STARTING] ===");
        
        // 1. Fetch live student statuses from Brevo upfront (prevents N+1 network lookups)
        Map<String, String> brevoStudentStatuses = communicationService.fetchStudentStatusMap();
        
        List<Student> allStudents = studentRepository.findByDeletedAtIsNull();
        LocalDateTime thresholdDateTime = LocalDateTime.now().minusDays(14);

        System.out.println("[CRON] Reconciling " + (allStudents != null ? allStudents.size() : 0) + " active students...");

        if (allStudents == null || allStudents.isEmpty()) {
            System.out.println("=== [CRON BACKGROUND SYNC COMPLETE] ===\n");
            return;
        }

        for (Student student : allStudents) {
            String studentName = student.getName();
            String accountId = student.getAccountId();

            if (studentName == null || accountId == null || studentName.isBlank() || accountId.isBlank()) {
                continue;
            }

            List<Booking> historicalBookings = bookingRepository.findByStudentId(student.getId());
            boolean hasRecentConfirmedBooking = false;
            boolean hasUpcomingConfirmedBooking = false;

            if (historicalBookings != null) {
                for (Booking booking : historicalBookings) {
                    if (booking.getDeletedAt() != null || "cancelled".equalsIgnoreCase(booking.getStatus())) {
                        continue;
                    }
                    LocalDateTime start = booking.getStartTime();
                    if (start != null) {
                        if (start.isAfter(thresholdDateTime) && start.isBefore(LocalDateTime.now())) {
                            hasRecentConfirmedBooking = true;
                        }
                        if (start.isAfter(LocalDateTime.now())) {
                            hasUpcomingConfirmedBooking = true;
                        }
                    }
                }
            }

            boolean lapsedNow = !hasRecentConfirmedBooking && !hasUpcomingConfirmedBooking;

            // 2. Resolve true current status from Brevo map instead of defaulting to false
            String statusFromBrevo = brevoStudentStatuses.getOrDefault(studentName.trim().toLowerCase(), "Active");
            boolean isLapsedInBrevo = "Lapsed".equalsIgnoreCase(statusFromBrevo.trim());

            Optional<AlertStudent> existingAlertOpt = alertStudentRepository.findById(studentName.trim());
            
            AlertStudent alertRow;
            if (existingAlertOpt.isPresent()) {
                alertRow = existingAlertOpt.get();
            } else {
                alertRow = new AlertStudent();
                alertRow.setName(studentName.trim());
            }

            alertRow.setAccountId(accountId.trim());
            alertRow.setLapsedNow(lapsedNow);
            alertRow.setLapsedStatus(isLapsedInBrevo); // DYNAMIC RECONCILIATION SUCCESS!
            alertRow.setLastCheckedAt(LocalDateTime.now());
            
            alertStudentRepository.save(alertRow);
        }
        System.out.println("CRON SUCCESS: Calendar anomalies successfully aligned.");
        System.out.println("=== [CRON BACKGROUND SYNC COMPLETE] ===\n");
    }
}