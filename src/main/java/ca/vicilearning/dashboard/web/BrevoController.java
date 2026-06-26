package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.comms.BrevoCommunicationService;
import ca.vicilearning.dashboard.rules.MockTaskService; 
import ca.vicilearning.dashboard.rules.BrevoReviewTask;  

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/comms")
public class BrevoController {

    private final BrevoCommunicationService communicationService;
    private final MockTaskService mockTaskService; 

    public BrevoController(BrevoCommunicationService communicationService, MockTaskService mockTaskService) {
        this.communicationService = communicationService;
        this.mockTaskService = mockTaskService;
    }

    @GetMapping("/review")
    public String reviewQueuePage(Model model) {
        model.addAttribute("pendingTasks", mockTaskService.getSimulatedSyncTasks());
        return "comms-review";
    }

    @PostMapping("/approve")
    public String approveTask(
            @RequestParam("taskId") Long taskId,
            @RequestParam("email") String email,
            @RequestParam("familyName") String familyName,
            @RequestParam("viciAccountId") String viciAccountId,
            @RequestParam("templateId") Long templateId,
            @RequestParam("reason") String reason,
            @RequestParam("paymentStatus") String paymentStatus,
            @RequestParam("studentNames") String studentNames,
            @RequestParam("bookingDates") String bookingDates) {

        System.out.println("====== SYSTEM REVIEW INTERACTION EXECUTED ======");
        System.out.println("Approving Review Tracker Item ID: " + taskId);
        System.out.println("Processing flat structures for family: " + familyName);

        // Step 1: Force synchronization with Brevo's horizontal CRM grid columns [cite: 15, 251]
        // Sends cleanly formatted parallel arrays straight to the API [cite: 320, 323, 327]
        Map<String, Object> crmAttributes = Map.of(
            "VICI_ACCOUNT_ID", viciAccountId,
            "STUDENT_NAMES", studentNames,
            "PAYMENT_STATUS", paymentStatus,
            "LAST_BOOKING_DATE", bookingDates
        );
        communicationService.updateContactAttributes(email, crmAttributes);

        // Step 2: Assemble localized tokens mapped directly into active transaction templates [cite: 19, 20]
        Map<String, Object> emailParams = Map.of(
            "CONTACT_NAME", familyName,
            "STUDENT_NAMES", studentNames,
            "TRIGGER_REASON", reason,
            "PAYMENT_STATUS", paymentStatus
        );

        // Step 3: Command the outbound message pipeline execution loop [cite: 20]
        boolean success = communicationService.sendTemplatedEmail(email, familyName, templateId, emailParams);

        if (success) {
            System.out.println("STATUS SUCCESS: Outbound event payload logged securely by Brevo servers.");
        } else {
            System.err.println("STATUS FAILURE: Processing error. Check daily cap metrics or API key parameters.");
        }
        System.out.println("=================================================");

        return "redirect:/comms/review";
    }
}