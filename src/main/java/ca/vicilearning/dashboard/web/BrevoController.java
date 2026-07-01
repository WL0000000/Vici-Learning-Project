package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.comms.BrevoCommunicationService;
import ca.vicilearning.dashboard.domain.AlertStudent;
import ca.vicilearning.dashboard.domain.AlertStudentRepository;
import ca.vicilearning.dashboard.domain.StudentRepository;
import ca.vicilearning.dashboard.sync.BrevoSyncEngineService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/comms")
public class BrevoController {

    private final BrevoCommunicationService communicationService;
    private final AlertStudentRepository alertStudentRepository;
    private final StudentRepository studentRepository;
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
                           StudentRepository studentRepository,
                           BrevoSyncEngineService syncEngineService) {
        this.communicationService = communicationService;
        this.alertStudentRepository = alertStudentRepository;
        this.studentRepository = studentRepository;
        this.syncEngineService = syncEngineService;
    }

    @GetMapping("/review")
    public String reviewQueuePage(Model model) {
        System.out.println("[CONTROLLER] Loading Automations Review Queue view...");
        
        List<AlertStudent> localAlerts = alertStudentRepository.findDiscrepancies();
        List<PendingTaskViewNode> viewTasks = new ArrayList<>();
        Map<String, String> crmEmailDictionary = communicationService.fetchViciIdToEmailMap();

        if (localAlerts != null) {
            for (AlertStudent alert : localAlerts) {
                String lookupKey = (alert.getAccountId() != null) ? alert.getAccountId().trim().toUpperCase() : "";
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
        
        System.out.println("\n=== [CONTROLLER APPROVE ACTION] ===");
        System.out.println("Processing approval for student: " + studentName + " (Account ID: " + viciAccountId + ")");

        try {
            if (email == null || email.isBlank() || "Not Found in Brevo".equalsIgnoreCase(email.trim())) {
                return "redirect:/comms/review?error=invalid_email_coordinate";
            }

            // 1. Locate ALL active sibling students sharing this parent account key
            List<ca.vicilearning.dashboard.domain.Student> familyMembers = studentRepository.findByDeletedAtIsNull().stream()
                    .filter(s -> s.getAccountId() != null && s.getAccountId().trim().equalsIgnoreCase(viciAccountId.trim()))
                    .toList();

            List<String> namesCollector = new ArrayList<>();
            List<String> statusCollector = new ArrayList<>();

            // 2. Safely re-compile parallel arrays to prevent sibling data corruption
            for (ca.vicilearning.dashboard.domain.Student student : familyMembers) {
                String currentName = student.getName().trim();
                namesCollector.add(currentName);

                String calculatedStatus = "Active";
                if (currentName.equalsIgnoreCase(studentName.trim())) {
                    if ("LAPSED".equalsIgnoreCase(actionType) || "SYNC_LAPSED".equalsIgnoreCase(actionType)) {
                        calculatedStatus = "Lapsed";
                    }
                } else {
                    Optional<AlertStudent> siblingAlert = alertStudentRepository.findById(currentName);
                    if (siblingAlert.isPresent() && siblingAlert.get().isLapsedNow()) {
                        calculatedStatus = "Lapsed";
                    }
                }
                statusCollector.add(calculatedStatus);
            }

            String delimitedNames = String.join(", ", namesCollector);
            String delimitedStatuses = String.join(", ", statusCollector);

            Map<String, Object> attributePayload = new HashMap<>();
            attributePayload.put("STUDENT_NAMES", delimitedNames);
            attributePayload.put("ACTIVITY_STATUS", delimitedStatuses);

            communicationService.updateContactAttributes(email, attributePayload);

            if ("SYNC_LAPSED".equalsIgnoreCase(actionType)) {
                Map<String, Object> emailParams = Map.of(
                    "STUDENT_NAME", studentName,
                    "TRIGGER_REASON", "No tutoring session completed or scheduled within the past 14 days."
                );
                communicationService.sendTemplatedEmail(email, studentName, 1L, emailParams);
            }

        } catch (Exception e) {
            System.err.println("[APPROVE CRITICAL EXCEPTION] Processing loop crashed:");
            e.printStackTrace();
        }

        alertStudentRepository.deleteById(studentName);
        System.out.println("=== [CONTROLLER APPROVE ACTION COMPLETE] ===\n");
        return "redirect:/comms/review";
    }

    @PostMapping("/sync-now")
    public String triggerImmediateSync() {
        try {
            syncEngineService.runTwoWayReconciliationSync();
        } catch (Exception e) {
            System.err.println("Immediate background engine sync failed: " + e.getMessage());
        }
        return "redirect:/comms/review";
    }
}