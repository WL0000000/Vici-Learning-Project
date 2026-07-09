package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.comms.BrevoCommunicationService;
import ca.vicilearning.dashboard.domain.AlertStudent;
import ca.vicilearning.dashboard.domain.AlertStudentRepository;
import ca.vicilearning.dashboard.domain.Student;
import ca.vicilearning.dashboard.domain.StudentRepository;
import ca.vicilearning.dashboard.sync.BrevoSyncEngineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Controller for handling manual synchronization approvals, alert queue tracking,
 * and triggering manual sync routines between the local database and Brevo CRM.
 */
@Controller
@RequestMapping("/comms")
public class BrevoController {

    private static final Logger log = LoggerFactory.getLogger(BrevoController.class);

    // Business Logic Constants
    private static final String NOT_FOUND_FALLBACK = "Not Found in Brevo";
    private static final String STATUS_ACTIVE = "Active";
    private static final String STATUS_LAPSED = "Lapsed";
    
    // Action Configuration Constants
    private static final String ACTION_LAPSED = "LAPSED";
    private static final String ACTION_SYNC_LAPSED = "SYNC_LAPSED";
    
    // Redirect Route Templates
    private static final String REDIRECT_REVIEW = "redirect:/comms/review";
    private static final String REDIRECT_REVIEW_ERR_EMAIL = "redirect:/comms/review?error=invalid_email_coordinate";
    
    // Transaction Constants
    private static final long LAPSED_EMAIL_TEMPLATE_ID = 1L;
    private static final String LAPSED_EMAIL_REASON = "No tutoring session completed or scheduled within the past 14 days.";

    private final BrevoCommunicationService communicationService;
    private final AlertStudentRepository alertStudentRepository;
    private final StudentRepository studentRepository;
    private final BrevoSyncEngineService syncEngineService;

    /**
     * DTO projection mapping data parameters out to the view layout table.
     */
    public record PendingTaskViewNode(
        String name,
        String accountId,
        boolean lapsedNow,
        boolean lapsedStatus,
        LocalDateTime lastCheckedAt,
        String parentEmail
    ) {}

    public BrevoController(BrevoCommunicationService communicationService, 
                           AlertStudentRepository alertStudentRepository,
                           StudentRepository studentRepository,
                           BrevoSyncEngineService syncEngineService) {
        this.communicationService = communicationService;
        this.alertStudentRepository = alertStudentRepository;
        this.studentRepository = studentRepository;
        this.syncEngineService = syncEngineService;
    }

    /**
     * Renders the master Automations Review Queue page.
     * Fault-tolerant against external API errors or missing credentials.
     */
    @GetMapping("/review")
    public String reviewQueuePage(Model model) {
        log.info("Loading Automations Review Queue...");
        
        List<AlertStudent> localAlerts = alertStudentRepository.findDiscrepancies();
        List<PendingTaskViewNode> viewTasks = new ArrayList<>();
        Map<String, String> crmEmailDictionary;

        try {
            crmEmailDictionary = communicationService.fetchViciIdToEmailMap();
        } catch (Exception e) {
            log.error("Failed to fetch VICI ID to Email mapping. Falling back to empty dataset.", e);
            crmEmailDictionary = Collections.emptyMap();
            model.addAttribute("brevoSystemOfflineWarning", true);
        }

        if (localAlerts != null) {
            for (AlertStudent alert : localAlerts) {
                String lookupKey = (alert.getAccountId() != null) ? alert.getAccountId().trim().toUpperCase() : "";
                String displayEmail = crmEmailDictionary.getOrDefault(lookupKey, NOT_FOUND_FALLBACK);

                viewTasks.add(new PendingTaskViewNode(
                    alert.getName(),
                    alert.getAccountId(),
                    alert.isLapsedNow(),
                    alert.isLapsedStatus(),
                    alert.getLastCheckedAt(),
                    displayEmail
                ));
            }
        }

        model.addAttribute("pendingTasks", viewTasks);
        return "comms-review";
    }

    /**
     * Approves an internal timeline discrepancy and recalculates the 
     * flat comma-separated sibling attributes to overwrite remote CRM data.
     */
    @PostMapping("/approve")
    public String approveAnomalySync(@RequestParam("studentName") String studentName,
                                     @RequestParam("viciAccountId") String viciAccountId,
                                     @RequestParam("email") String email,
                                     @RequestParam("actionType") String actionType) {
        
        log.info("Processing manual action approval for student: {} (Account: {})", studentName, viciAccountId);

        try {
            if (email == null || email.isBlank() || NOT_FOUND_FALLBACK.equalsIgnoreCase(email.trim())) {
                log.warn("Aborted processing: Missing or unresolvable fallback email address context coordinate.");
                return REDIRECT_REVIEW_ERR_EMAIL;
            }

            // Locate active sibling student profiles sharing this parent account context
            List<Student> familyMembers = studentRepository.findByDeletedAtIsNull().stream()
                    .filter(s -> s.getAccountId() != null && s.getAccountId().trim().equalsIgnoreCase(viciAccountId.trim()))
                    .toList();

            Map<String, Object> attributePayload = compileFamilyAttributesPayload(studentName, actionType, familyMembers);

            communicationService.updateContactAttributes(email, attributePayload);

            // Dispatch notice message tracking loops out if specified
            if (ACTION_SYNC_LAPSED.equalsIgnoreCase(actionType)) {
                Map<String, Object> emailParams = Map.of(
                    "STUDENT_NAME", studentName,
                    "TRIGGER_REASON", LAPSED_EMAIL_REASON
                );
                communicationService.sendTemplatedEmail(email, studentName, LAPSED_EMAIL_TEMPLATE_ID, emailParams);
            }

        } catch (Exception e) {
            log.error("Error encountered executing approval synchronization rules for: {}", studentName, e);
        }

        alertStudentRepository.deleteById(studentName);
        return REDIRECT_REVIEW;
    }

    /**
     * Forces an immediate background reconciliation engine processing sequence execution window.
     */
    @PostMapping("/sync-now")
    public String triggerImmediateSync() {
        try {
            log.info("Manual synchronization trigger invoked from client session registry.");
            syncEngineService.runTwoWayReconciliationSync();
        } catch (Exception e) {
            log.error("Manual out-of-band engine synchronization step execution failed completely.", e);
        }
        return REDIRECT_REVIEW;
    }

    /**
     * Builds the structured parallel multi-value attribute map for family groups.
     */
    private Map<String, Object> compileFamilyAttributesPayload(String targetStudentName, String actionType, List<Student> familyMembers) {
        List<String> namesCollector = new ArrayList<>();
        List<String> statusCollector = new ArrayList<>();

        for (Student student : familyMembers) {
            String currentName = student.getName().trim();
            namesCollector.add(currentName);

            String calculatedStatus = STATUS_ACTIVE;
            
            if (currentName.equalsIgnoreCase(targetStudentName.trim())) {
                if (ACTION_LAPSED.equalsIgnoreCase(actionType) || ACTION_SYNC_LAPSED.equalsIgnoreCase(actionType)) {
                    calculatedStatus = STATUS_LAPSED;
                }
            } else {
                Optional<AlertStudent> siblingAlert = alertStudentRepository.findById(currentName);
                if (siblingAlert.isPresent() && siblingAlert.get().isLapsedNow()) {
                    calculatedStatus = STATUS_LAPSED;
                }
            }
            statusCollector.add(calculatedStatus);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("STUDENT_NAMES", String.join(", ", namesCollector));
        payload.put("ACTIVITY_STATUS", String.join(", ", statusCollector));
        
        return payload;
    }
}