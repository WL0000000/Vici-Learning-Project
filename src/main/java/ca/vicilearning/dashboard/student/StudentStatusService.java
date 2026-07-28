package ca.vicilearning.dashboard.student;

import ca.vicilearning.dashboard.comms.BrevoCommunicationService;
import ca.vicilearning.dashboard.domain.Student;
import ca.vicilearning.dashboard.domain.StudentRepository;
import ca.vicilearning.dashboard.domain.StudentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * The staff/admin write-path for a student's ACTIVE/PAUSED enrolment status (Meeting #4 follow-up):
 * lets a dashboard user set status instead of it being read-only. Status display/filtering stays in
 * {@code DashboardMetricsService}; this class only mutates.
 *
 * <p><b>Authoritative.</b> An edit is written to BOTH the local column AND back to Brevo's
 * {@code STUDENT_STATUS} attribute — the declared source of truth — so the next hourly
 * {@code statuses} sync <em>reinforces</em> the edit rather than reverting it. (Without the
 * write-back the local value is overwritten on the next sync for any student Brevo already tracks.)
 * Because ~most contacts have no {@code STUDENT_STATUS} set upstream, a dashboard edit also
 * <em>populates</em> it in Brevo, so the dashboard enriches Brevo rather than just mirroring it.
 *
 * <p>The Brevo write is <b>best-effort</b>: it is keyed by the student's email and skipped when the
 * student has none, and {@link BrevoCommunicationService#updateContactAttributes} already
 * swallows/logs any API failure (missing or read-only key, outage). So a failed push never breaks
 * the local edit — it only means that, for a Brevo-tracked student, a later sync could still revert
 * it (exactly the pre-write-back behaviour).
 */
@Service
public class StudentStatusService {

    private static final Logger log = LoggerFactory.getLogger(StudentStatusService.class);

    // The Brevo per-contact attribute holding a student's ACTIVE/PAUSED status. MUST match the
    // attribute the statuses sync step reads (BrevoCommunicationService#fetchEmailToStatusMap), so a
    // write here and the next read agree.
    private static final String BREVO_STATUS_ATTRIBUTE = "STUDENT_STATUS";

    private final StudentRepository studentRepo;
    private final BrevoCommunicationService brevo;

    public StudentStatusService(StudentRepository studentRepo, BrevoCommunicationService brevo) {
        this.studentRepo = studentRepo;
        this.brevo = brevo;
    }

    /**
     * Set a student's enrolment status locally and push it back to Brevo. No-op (returns
     * {@code false}) when the id is unknown or the status string isn't a recognized
     * {@link StudentStatus}, so a malformed request can neither 500 nor blank the column.
     *
     * <p>Not {@code @Transactional}: the Brevo call is external HTTP and must run outside any DB
     * transaction. The local write goes through {@code save()} (its own transaction) first, so it
     * lands regardless of what the network call does.
     *
     * @return {@code true} if a student was found and updated locally
     */
    public boolean setStatus(Long studentId, String status) {
        StudentStatus parsed = parse(status);
        if (studentId == null || parsed == null) {
            return false;
        }
        return studentRepo.findById(studentId)
                .map(s -> {
                    s.setStatus(parsed);
                    studentRepo.save(s);
                    pushToBrevo(s, parsed);
                    return true;
                })
                .orElse(false);
    }

    /** Write the status back to Brevo (keyed by email) so it stays the source of truth. Best-effort. */
    private void pushToBrevo(Student student, StudentStatus status) {
        String email = student.getEmail();
        if (email == null || email.isBlank()) {
            log.info("Student {} has no email; status set locally only (not pushed to Brevo)", student.getId());
            return;
        }
        brevo.updateContactAttributes(email.trim(), Map.of(BREVO_STATUS_ATTRIBUTE, status.name()));
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
