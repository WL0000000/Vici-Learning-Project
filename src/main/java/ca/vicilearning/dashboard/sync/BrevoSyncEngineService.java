package ca.vicilearning.dashboard.sync;

import ca.vicilearning.dashboard.association.AccountIdNormalizer;
import ca.vicilearning.dashboard.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Compares our own booking-derived lapse status against Brevo's real roster status
// (RosterStudent.status, synced from CONTACT_STATUS), linked by the student's family account
// key. Used to key off a VICI_ACCOUNT_ID contact attribute plus comma-joined STUDENT_NAMES/
// ACTIVITY_STATUS attributes that don't actually seem to exist on the real account, so the
// lookup missed almost everyone and defaulted to "Active", making basically every lapsed
// student look like a false discrepancy. RosterStudent.status is the same source that's
// already proven reliable for the Tutor Portal's My Students page.
@Service
public class BrevoSyncEngineService {

    private static final Logger log = LoggerFactory.getLogger(BrevoSyncEngineService.class);

    private final StudentRepository studentRepository;
    private final BookingRepository bookingRepository;
    private final AlertStudentRepository alertStudentRepository;
    private final RosterStudentRepository rosterStudentRepository;

    // Shared with DashboardMetricsService (same property) so the Brevo reconciliation and the
    // dashboard action items agree on who's lapsed — previously 14 here vs 21 there.
    private final int lapseThresholdDays;

    public BrevoSyncEngineService(StudentRepository studentRepository,
                                  BookingRepository bookingRepository,
                                  AlertStudentRepository alertStudentRepository,
                                  RosterStudentRepository rosterStudentRepository,
                                  @Value("${metrics.lapse-threshold-days:21}") int lapseThresholdDays) {
        this.studentRepository = studentRepository;
        this.bookingRepository = bookingRepository;
        this.alertStudentRepository = alertStudentRepository;
        this.rosterStudentRepository = rosterStudentRepository;
        this.lapseThresholdDays = lapseThresholdDays;
    }

    // runs hourly, pulls everything up front so the per-student loop stays in memory
    @Scheduled(cron = "0 0 * * * *")
    public void runTwoWayReconciliationSync() {
        log.info("Initiating automatic structured Brevo synchronization sequence...");

        // group roster by family account key so a SimplyBook Student can be matched against its
        // sibling roster records, same tolerant matching as the rest of the app (AccountIdNormalizer,
        // handles "Gray" vs "Gray_Account")
        Map<String, List<RosterStudent>> rosterByAccountKey = rosterStudentRepository.findByDeletedAtIsNull().stream()
                .filter(r -> r.getAccountId() != null && !r.getAccountId().isBlank())
                .collect(Collectors.groupingBy(r -> AccountIdNormalizer.compareKey(r.getAccountId())));

        List<Student> activeStudents = studentRepository.findByDeletedAtIsNull();
        LocalDateTime thresholdDateTime = LocalDateTime.now(AppClock.ZONE).minusDays(lapseThresholdDays);

        log.info("Synchronizing {} student tracking configurations...", activeStudents.size());
        for (Student student : activeStudents) {
            try {
                reconcileSingleStudent(student, rosterByAccountKey, thresholdDateTime);
            } catch (Exception ex) {
                log.error("Anomalous fault encountered running alignment computations for ID: {}", student.getId(), ex);
            }
        }

        purgeOrphanedAlerts(activeStudents);
        log.info("Two-way tracking alignment routines finalized successfully.");
    }

    // cleans up alerts for students who no longer show up as active (removed from SimplyBook
    // entirely, say). the loop above only visits active students, so without this a removed
    // student's old alert would just sit there forever

    private void purgeOrphanedAlerts(List<Student> activeStudents) {
        Set<String> activeNames = activeStudents.stream()
                .map(Student::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());

        for (AlertStudent alert : alertStudentRepository.findAll()) {
            if (!activeNames.contains(alert.getName())) {
                alertStudentRepository.deleteById(alert.getName());
            }
        }
    }

    private void reconcileSingleStudent(Student student, Map<String, List<RosterStudent>> rosterByAccountKey,
                                        LocalDateTime baselineThreshold) {
        String studentName = student.getName();
        String accountId = student.getAccountId();

        if (studentName == null || accountId == null || studentName.isBlank() || accountId.isBlank()) {
            return;
        }

        List<Booking> structuralBookings = bookingRepository.findByStudentId(student.getId());
        boolean lapsedNow = evaluateLapseCondition(structuralBookings, baselineThreshold);

        RosterStudent matched = findMatchingRosterStudent(studentName, accountId, rosterByAccountKey);
        if (matched == null) {
            // no matching roster record, nothing to compare against, don't guess
            alertStudentRepository.deleteById(studentName.trim());
            return;
        }

        // ACTIVE/PAUSED means Brevo still considers them a student, DROPPED/COMPLETED means gone
        boolean isLapsedInBrevo = !matched.getStatus().isCurrent();

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
        alertEntity.setLastCheckedAt(LocalDateTime.now(AppClock.ZONE));
        alertEntity.setEmail(student.getEmail());

        alertStudentRepository.save(alertEntity);
    }

    /** Finds this student's own roster record among their family's roster siblings, by name. */
    private RosterStudent findMatchingRosterStudent(String studentName, String accountId,
                                                     Map<String, List<RosterStudent>> rosterByAccountKey) {
        List<RosterStudent> siblings = rosterByAccountKey.get(AccountIdNormalizer.compareKey(accountId));
        if (siblings == null) {
            return null;
        }
        String normalizedName = NameNormalizer.normalize(studentName);
        if (normalizedName == null) {
            return null;
        }
        for (RosterStudent r : siblings) {
            if (normalizedName.equalsIgnoreCase(NameNormalizer.normalize(r.getName()))) {
                return r;
            }
        }
        return null;
    }

    // true if this student has no recent or upcoming booking, i.e. lapsed
    private boolean evaluateLapseCondition(List<Booking> bookings, LocalDateTime baselineThreshold) {
        if (bookings == null || bookings.isEmpty()) {
            return true;
        }

        boolean hasRecentConfirmedBooking = false;
        boolean hasUpcomingConfirmedBooking = false;
        LocalDateTime now = LocalDateTime.now(AppClock.ZONE);

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
