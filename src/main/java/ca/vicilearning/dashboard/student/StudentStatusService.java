package ca.vicilearning.dashboard.student;

import ca.vicilearning.dashboard.domain.RosterStudentRepository;
import ca.vicilearning.dashboard.domain.StudentStatus;
import org.springframework.stereotype.Service;

/**
 * The staff/admin write-path for a student's enrolment status — lets a dashboard user override the
 * status on the Brevo-sourced roster ({@code RosterStudent}, keyed by EXT_ID). Display/filtering
 * lives in {@code DashboardMetricsService}; this class only mutates.
 *
 * <p><b>Phase 1: local override.</b> The status source of truth is Brevo's {@code CONTACT_STATUS}
 * (a list-typed attribute), pulled by the roster sync — so an edit here is overwritten on the next
 * sync for a student Brevo tracks. A proper authoritative write-back (PUT the CONTACT_STATUS
 * category, keyed by EXT_ID) is a follow-up: it needs the list-attribute write format verified
 * against Vici's Brevo (blocked while the API keys are revoked). Until then this is a local override.
 */
@Service
public class StudentStatusService {

    private final RosterStudentRepository rosterStudentRepo;

    public StudentStatusService(RosterStudentRepository rosterStudentRepo) {
        this.rosterStudentRepo = rosterStudentRepo;
    }

    /**
     * Set a roster student's status by EXT_ID. No-op (returns {@code false}) when the id is unknown or
     * the status isn't a recognized {@link StudentStatus}, so a malformed request can't 500 or blank
     * the column.
     *
     * @return {@code true} if a student was found and updated
     */
    public boolean setStatus(String extId, String status) {
        StudentStatus parsed = StudentStatus.fromBrevo(status);
        if (extId == null || extId.isBlank() || parsed == null) {
            return false;
        }
        return rosterStudentRepo.findById(extId)
                .map(r -> {
                    r.setStatus(parsed);
                    rosterStudentRepo.save(r);
                    return true;
                })
                .orElse(false);
    }
}
