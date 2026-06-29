package ca.vicilearning.dashboard.sync;

import ca.vicilearning.dashboard.domain.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BrevoSyncEngineService {

    private final StudentRepository studentRepository;
    private final BookingRepository bookingRepository;
    private final AlertStudentRepository alertStudentRepository;

    public BrevoSyncEngineService(StudentRepository studentRepository,
                                  BookingRepository bookingRepository,
                                  AlertStudentRepository alertStudentRepository) {
        this.studentRepository = studentRepository;
        this.bookingRepository = bookingRepository;
        this.alertStudentRepository = alertStudentRepository;
    }

    /**
     * Automated Background Reconciliation: Scans booking history tables every hour 
     * on the hour to isolate active student calendar anomalies.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void runTwoWayReconciliationSync() {
        System.out.println("\n=== [CRON BACKGROUND SYNC STARTING] ===");
        System.out.println("Refreshing calendar lookup thresholds...");
        
        List<Student> allStudents = studentRepository.findByDeletedAtIsNull();
        LocalDateTime thresholdDateTime = LocalDateTime.now().minusDays(14);

        System.out.println("[CRON] Processing " + (allStudents != null ? allStudents.size() : 0) + " active students...");

        if (allStudents == null || allStudents.isEmpty()) {
            System.out.println("[CRON] No active students found in database. Exiting loop.");
            System.out.println("=== [CRON BACKGROUND SYNC COMPLETE] ===\n");
            return;
        }

        for (Student student : allStudents) {
            String studentName = student.getName();
            String accountId = student.getAccountId();

            // Guard against broken relational data links
            if (studentName == null || accountId == null || studentName.isBlank() || accountId.isBlank()) {
                continue;
            }

            // Fetch booking milestones for this individual student profile
            List<Booking> historicalBookings = bookingRepository.findByStudentId(student.getId());
            boolean hasRecentConfirmedBooking = false;
            boolean hasUpcomingConfirmedBooking = false;

            if (historicalBookings != null) {
                for (Booking booking : historicalBookings) {
                    // Step over deleted or canceled appointments entirely
                    if (booking.getDeletedAt() != null || "cancelled".equalsIgnoreCase(booking.getStatus())) {
                        continue;
                    }
                    
                    LocalDateTime start = booking.getStartTime();
                    if (start != null) {
                        // Check Lookback Window: Confirmed lesson within the last 14 days?
                        if (start.isAfter(thresholdDateTime) && start.isBefore(LocalDateTime.now())) {
                            hasRecentConfirmedBooking = true;
                        }
                        // Check Future Window: Confirmed lesson booked ahead?
                        if (start.isAfter(LocalDateTime.now())) {
                            hasUpcomingConfirmedBooking = true;
                        }
                    }
                }
            }

            // Calculate current local state reality
            boolean lapsedNow = !hasRecentConfirmedBooking && !hasUpcomingConfirmedBooking;

            // FIX: Check if an alert row tracking this student name already exists in the ledger table
            Optional<AlertStudent> existingAlertOpt = alertStudentRepository.findById(studentName.trim());
            
            AlertStudent alertRow;
            if (existingAlertOpt.isPresent()) {
                // Relocate existing row to preserve historical admin approvals
                alertRow = existingAlertOpt.get();
                System.out.println("[CRON TRACE] Updating status tracking ledger for existing student: " + studentName.trim());
            } else {
                // Initialize fresh row tracking only if it's a completely new student record
                alertRow = new AlertStudent();
                alertRow.setName(studentName.trim());
                alertRow.setLapsedStatus(false); // Safely default new entries to false
                System.out.println("[CRON TRACE] Creating fresh tracking snapshot row for new student: " + studentName.trim());
            }

            // Update dynamically calculated status and check times
            alertRow.setAccountId(accountId.trim());
            alertRow.setLapsedNow(lapsedNow);
            alertRow.setLastCheckedAt(LocalDateTime.now());
            
            // Persist the row state cleanly back down to PostgreSQL / H2 ledger tables
            alertStudentRepository.save(alertRow);
        }
        
        System.out.println("CRON SUCCESS: Calendar anomaly ledgers successfully aligned.");
        System.out.println("=== [CRON BACKGROUND SYNC COMPLETE] ===\n");
    }
}