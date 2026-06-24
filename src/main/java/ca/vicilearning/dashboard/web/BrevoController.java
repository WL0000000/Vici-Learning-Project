package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.comms.BrevoCommunicationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/comms")
public class BrevoController {

    private final BrevoCommunicationService communicationService;

    // Direct dependency injection, exactly like SyncController
    public BrevoController(BrevoCommunicationService communicationService) {
        this.communicationService = communicationService;
    }

    @GetMapping("/review")
    public String reviewQueuePage(Model model) {
        // Mock data layer referencing your functional sandbox contact address
        List<BrevoReviewTask> mockTasks = List.of(
            new BrevoReviewTask(1L, "Miata Boy", "miataboy100@gmail.com", "Lapsed: No bookings observed in 14 days", 1L),
            new BrevoReviewTask(2L, "Jane Doe", "jane.doe@example.com", "Payment Status: Overdue Invoice", 2L)
        );

        model.addAttribute("pendingTasks", mockTasks);
        return "comms-review"; // Routes to templates/comms-review.html
    }

    @PostMapping("/approve")
    public String approveTask(
            @RequestParam("email") String email,
            @RequestParam("name") String name,
            @RequestParam("templateId") Long templateId,
            @RequestParam("reason") String reason) {

        // Build dynamic parameters payload corresponding to your Brevo email design placeholders
        Map<String, Object> emailParams = new HashMap<>();
        emailParams.put("CONTACT_NAME", name);
        emailParams.put("TRIGGER_REASON", reason);

        // Dispatch live request to Brevo via your RestClient Service layer
        boolean emailSent = communicationService.sendTemplatedEmail(email, name, templateId, emailParams);

        if (emailSent) {
            System.out.println("Prototype log: Successfully dispatched Brevo communication to " + email);
        } else {
            System.err.println("Prototype log: Failed to dispatch communication to " + email);
        }

        // Redirect back to main review dashboard view, maintaining consistent group design patterns
        return "redirect:/comms/review";
    }
}