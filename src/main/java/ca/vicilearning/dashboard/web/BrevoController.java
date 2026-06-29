package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.comms.BrevoCommunicationService;
import ca.vicilearning.dashboard.domain.AlertStudent;
import ca.vicilearning.dashboard.domain.AlertStudentRepository;
import ca.vicilearning.dashboard.sync.BrevoSyncEngineService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/comms")
public class BrevoController {

    private final BrevoCommunicationService communicationService;
    private final AlertStudentRepository alertStudentRepository;
    private final BrevoSyncEngineService syncEngineService;

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
                           BrevoSyncEngineService syncEngineService) {
        this.communicationService = communicationService;
        this.alertStudentRepository = alertStudentRepository;
        this.syncEngineService = syncEngineService;
    }

    /**
     * Renders the administrative review page dashboard grid queue.
     * Executes exactly ONE single batch request to fetch contacts upfront.
     */
    @GetMapping("/review")
    public String reviewQueuePage(Model model) {
        System.out.println("[CONTROLLER] Loading Automations Review Queue view...");
        
        List<AlertStudent> localAlerts = alertStudentRepository.findDiscrepancies();
        List<PendingTaskViewNode> viewTasks = new ArrayList<>();

        // ONE elegant network call, fetching everything in a single transaction
        Map<String, String> crmEmailDictionary = communicationService.fetchViciIdToEmailMap();

        if (localAlerts != null) {
            for (AlertStudent alert : localAlerts) {
                String lookupKey = (alert.getAccountId() != null) ? alert.getAccountId().trim().toUpperCase() : "";
                
                // Instant look up resolution in memory! No trailing iterative network requests.
                String displayEmail = crmEmailDictionary.getOrDefault(lookupKey, "Not Found in Brevo");

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

    @PostMapping("/approve")
    public String approveAnomalySync(@RequestParam("studentName") String studentName,
                                     @RequestParam("viciAccountId") String viciAccountId,
                                     @RequestParam("email") String email,
                                     @RequestParam("actionType") String actionType) {
        try {
            if (email == null || email.isBlank() || "Not Found in Brevo".equalsIgnoreCase(email.trim())) {
                return "redirect:/comms/review?error=invalid_email_coordinate";
            }

            String calculatedStatus = "Active";
            if ("LAPSED".equalsIgnoreCase(actionType) || "SYNC_LAPSED".equalsIgnoreCase(actionType)) {
                calculatedStatus = "Lapsed";
            }

            Map<String, Object> attributePayload = new HashMap<>();
            attributePayload.put("STUDENT_NAMES", studentName.trim());
            attributePayload.put("ACTIVITY_STATUS", calculatedStatus);

            communicationService.updateContactAttributes(email, attributePayload);

            if ("SYNC_LAPSED".equalsIgnoreCase(actionType)) {
                Map<String, Object> emailParams = Map.of("STUDENT_NAME", studentName, "TRIGGER_REASON", "No tutoring session completed within 14 days.");
                communicationService.sendTemplatedEmail(email, studentName, 1L, emailParams);
            }
        } catch (Exception e) {
            System.err.println("[APPROVE EXCEPTION] " + e.getMessage());
        }

        alertStudentRepository.deleteById(studentName);
        return "redirect:/comms/review";
    }

    @PostMapping("/sync-now")
    public String triggerImmediateSync() {
        try {
            syncEngineService.runTwoWayReconciliationSync();
        } catch (Exception e) {
            System.err.println("Sync failed: " + e.getMessage());
        }
        return "redirect:/comms/review";
    }
}