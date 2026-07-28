package ca.vicilearning.dashboard.student;

import ca.vicilearning.dashboard.domain.StudentRepository;
import ca.vicilearning.dashboard.domain.StudentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one staff write-path for a student's ACTIVE/PAUSED enrolment status — the Meeting #4
 * follow-up that lets an admin flip status from the dashboard instead of it being read-only
 * (seeded, or synced from Brevo). Status display/filtering stays in {@code DashboardMetricsService};
 * this class only mutates.
 *
 * <p><b>Interaction with the Brevo sync:</b> {@code SyncService}'s {@code statuses} step treats
 * Brevo's {@code STUDENT_STATUS} as the source of truth and overrides the local value on the next
 * run when Brevo has a recognized status for that student. So a manual toggle here is authoritative
 * only until the next sync overwrites it. Making manual edits "sticky" (or pushing them back to
 * Brevo) is a separate decision — see the student-status notes.
 */
@Service
public class StudentStatusService {

    private final StudentRepository studentRepo;

    public StudentStatusService(StudentRepository studentRepo) {
        this.studentRepo = studentRepo;
    }

    /**
     * Set a student's enrolment status. No-op (returns {@code false}) when the id is unknown or the
     * status string isn't a recognized {@link StudentStatus}, so a malformed request can neither 500
     * nor blank the column.
     *
     * @return {@code true} if a student was found and updated
     */
    @Transactional
    public boolean setStatus(Long studentId, String status) {
        StudentStatus parsed = parse(status);
        if (studentId == null || parsed == null) {
            return false;
        }
        return studentRepo.findById(studentId)
                .map(s -> {
                    s.setStatus(parsed);
                    studentRepo.save(s);
                    return true;
                })
                .orElse(false);
    }

    private static StudentStatus parse(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return StudentStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
