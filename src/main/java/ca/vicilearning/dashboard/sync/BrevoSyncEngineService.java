package ca.vicilearning.dashboard.sync;

import ca.vicilearning.dashboard.comms.BrevoCommunicationService;
import ca.vicilearning.dashboard.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Engine management system handling scheduled synchronization processing windows.
 * Reconciles local database states against distant telemetry datasets pulled out from Brevo CRM.
 */
@Service
public class BrevoSyncEngineService {

    private static final Logger log = LoggerFactory.getLogger(BrevoSyncEngineService.class);
    private static final String STATUS_LAPSED = "Lapsed";

    private final StudentRepository studentRepository;
    private final BookingRepository bookingRepository;
    private final AlertStudentRepository alertStudentRepository;
    private final BrevoCommunicationService communicationService;

    // Shared with DashboardMetricsService (same property) so the Brevo reconciliation and the
    // dashboard action items agree on who's lapsed — previously 14 here vs 21 there.
    private final int lapseThresholdDays;

    public BrevoSyncEngineService(StudentRepository studentRepository,
                                  BookingRepository bookingRepository,
                                  AlertStudentRepository alertStudentRepository,
                                  BrevoCommunicationService communicationService,
                                  @Value("${metrics.lapse-threshold-days:21}") int lapseThresholdDays) {
        this.studentRepository = studentRepository;
        this.bookingRepository = bookingRepository;
        this.alertStudentRepository = alertStudentRepository;
        this.communicationService = communicationService;
        this.lapseThresholdDays = lapseThresholdDays;
    }

    /**
     * Orchestrates background two-way synchronization windows scheduled sequentially.
     * Pulls bulk states up-front to run in-memory processing mappings efficiently.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void runTwoWayReconciliationSync() {
        log.info("Initiating automatic structured Brevo synchronization sequence...");
        
        // 1. Capture the multi-tiered structural configuration map
        Map<String, Map<String, String>> brevoStudentStatuses = communicationService.fetchStudentStatusMap();
        
        List<Student> activeStudents = studentRepository.findByDeletedAtIsNull();
        LocalDateTime thresholdDateTime = LocalDateTime.now().minusDays(lapseThresholdDays);
        
        log.info("Synchronizing {} student tracking configurations...", activeStudents.size());
        for (Student student : activeStudents) {
            try {
                reconcileSingleStudent(student, brevoStudentStatuses, thresholdDateTime);
            } catch (Exception ex) {
                log.error("Anomalous fault encountered running alignment computations for ID: {}", student.getId(), ex);
            }
        }
        log.info("Two-way tracking alignment routines finalized successfully.");
    }

    private void reconcileSingleStudent(Student student, Map<String, Map<String, String>> brevoStatuses, LocalDateTime baselineThreshold) {
        String studentName = student.getName();
        String accountId = student.getAccountId();

        if (studentName == null || accountId == null || studentName.isBlank() || accountId.isBlank()) {
            return;
        }

        List<Booking> structuralBookings = bookingRepository.findByStudentId(student.getId());
        boolean lapsedNow = evaluateLapseCondition(structuralBookings, baselineThreshold);

        // 2. SEARCH FOR STATUS VIA ACCOUNT ID FIRST:
        String statusFromBrevo = "Active"; // Default fallback if family record is missing
        
        Map<String, String> familyBucket = brevoStatuses.get(accountId.trim().toUpperCase());
        if (familyBucket != null) {
            // If the family account exists, look up the target child's name inside it
            statusFromBrevo = familyBucket.getOrDefault(studentName.trim().toLowerCase(), "Active");
        }

        boolean isLapsedInBrevo = STATUS_LAPSED.equalsIgnoreCase(statusFromBrevo.trim());

        // 3. Proactive cleanup database gate
        if (lapsedNow == isLapsedInBrevo) {
            alertStudentRepository.deleteById(studentName.trim());
            return; 
        }

        AlertStudent alertEntity = alertStudentRepository.findById(studentName.trim())
                .orElseGet(() -> {
                    AlertStudent newRecord = new AlertStudent();
                    newRecord.setName(studentName.trim());
                    return newRecord;
                });

        alertEntity.setAccountId(accountId.trim());
        alertEntity.setLapsedNow(lapsedNow);
        alertEntity.setLapsedStatus(isLapsedInBrevo);
        alertEntity.setLastCheckedAt(LocalDateTime.now());
        
        alertStudentRepository.save(alertEntity);
    }

    /**
     * Determines whether student tracking flows fall behind active operational constraints.
     */
    private boolean evaluateLapseCondition(List<Booking> bookings, LocalDateTime baselineThreshold) {
        if (bookings == null || bookings.isEmpty()) {
            return true;
        }

        boolean hasRecentConfirmedBooking = false;
        boolean hasUpcomingConfirmedBooking = false;
        LocalDateTime now = LocalDateTime.now();

        for (Booking booking : bookings) {
            // Drop cancelled or deleted bookings from data pool tracking pipelines
            if (booking.getDeletedAt() != null || "cancelled".equalsIgnoreCase(booking.getStatus())) {
                continue;
            }
            
            LocalDateTime startTime = booking.getStartTime();
            if (startTime != null) {
                if (startTime.isAfter(baselineThreshold) && startTime.isBefore(now)) {
                    hasRecentConfirmedBooking = true;
                }
                if (startTime.isAfter(now)) {
                    hasUpcomingConfirmedBooking = true;
                }
            }
        }

        return !hasRecentConfirmedBooking && !hasUpcomingConfirmedBooking;
    }
}