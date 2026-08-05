package ca.vicilearning.dashboard.student;

import ca.vicilearning.dashboard.comms.BrevoCommunicationService;
import ca.vicilearning.dashboard.domain.RosterStudentRepository;
import ca.vicilearning.dashboard.domain.StudentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The staff/admin write-path for a student's enrolment status — lets a dashboard user override the
 * status on the Brevo-sourced roster ({@code RosterStudent}, keyed by EXT_ID). Display/filtering
 * lives in {@code DashboardMetricsService}; this class only mutates.
 *
 * <p><b>Always a local override; optionally a Brevo write-back.</b> Setting the status updates the
 * local roster. When {@code brevo.status-writeback-enabled} is true it <i>also</i> writes the value
 * back to Brevo's {@code CONTACT_STATUS} attribute (keyed by EXT_ID) so the override survives the next
 * roster sync. The flag defaults <b>off</b> on purpose: the write-back is only safe once the Brevo
 * <i>read</i> (which field, which student) is verified against Vici's live account — writing on a
 * misread would corrupt her CRM. Flip the flag after that verification.
 */
@Service
public class StudentStatusService {

    private final RosterStudentRepository rosterStudentRepo;
    private final BrevoCommunicationService brevoService;
    private final boolean writeBackEnabled;

    public StudentStatusService(RosterStudentRepository rosterStudentRepo,
                                BrevoCommunicationService brevoService,
                                @Value("${brevo.status-writeback-enabled:false}") boolean writeBackEnabled) {
        this.rosterStudentRepo = rosterStudentRepo;
        this.brevoService = brevoService;
        this.writeBackEnabled = writeBackEnabled;
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
                    if (writeBackEnabled) {
                        // Authoritative write-back so the override survives the next roster sync.
                        brevoService.updateContactStatusByExtId(extId, parsed.brevoValue());
                    }
                    return true;
                })
                .orElse(false);
    }
}
