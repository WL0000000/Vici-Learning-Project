package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.comms.BrevoCommunicationService;
import ca.vicilearning.dashboard.rules.MockTaskService; // Import your new service file
import ca.vicilearning.dashboard.rules.BrevoReviewTask;  // Import the record layout
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/comms")
public class BrevoController {

    private final BrevoCommunicationService communicationService;
    private final MockTaskService mockTaskService; // Added as a clean dependency

    // Dependency injection constructor matching your group's architectural pattern
    public BrevoController(BrevoCommunicationService communicationService, MockTaskService mockTaskService) {
        this.communicationService = communicationService;
        this.mockTaskService = mockTaskService;
    }

    @GetMapping("/review")
    public String reviewQueuePage(Model model) {
        // Look how clean this is now! We just ask the separate service file for the data.
        model.addAttribute("pendingTasks", mockTaskService.getSimulatedSyncTasks());
        return "comms-review";
    }

    @PostMapping("/approve")
    public String approveTask(
            @RequestParam("email") String email,
            @RequestParam("name") String name,
            @RequestParam("templateId") Long templateId,
            @RequestParam("reason") String reason) {

        System.out.println("====== PROTOTYPE TRANSACTION TRIGGERED ======");
        System.out.println("Processing approval for family: " + name);
        System.out.println("Target Destination: " + email);
        System.out.println("Executing Template Identification ID: " + templateId);

        Map<String, Object> emailParams = new HashMap<>();
        emailParams.put("CONTACT_NAME", name);
        emailParams.put("TRIGGER_REASON", reason);

        boolean success = communicationService.sendTemplatedEmail(email, name, templateId, emailParams);

        if (success) {
            System.out.println("STATUS: Outbound delivery payload successfully accepted by Brevo servers!");
        } else {
            System.err.println("STATUS: Transmission failure. Check keys, templates, or daily caps.");
        }
        System.out.println("===============================================");

        return "redirect:/comms/review";
    }
}