package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.comms.BrevoCommunicationService;
import ca.vicilearning.dashboard.domain.AlertStudent;
import ca.vicilearning.dashboard.domain.AlertStudentRepository;
import ca.vicilearning.dashboard.rules.MockTaskService;
import ca.vicilearning.dashboard.sync.BrevoSyncEngineService;
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
    private final AlertStudentRepository alertStudentRepository;
    private final BrevoSyncEngineService syncEngineService;

    public BrevoController(BrevoCommunicationService communicationService, 
                           AlertStudentRepository alertStudentRepository,
                           BrevoSyncEngineService syncEngineService) {
        this.communicationService = communicationService;
        this.alertStudentRepository = alertStudentRepository;
        this.syncEngineService = syncEngineService;
    }

    @GetMapping("/review")
    public String reviewQueuePage(Model model) {
        // Automatically isolate conflicting profiles to hydrate the review rows
        List<AlertStudent> structuralDiscrepancies = alertStudentRepository.findDiscrepancies();
        
        model.addAttribute("pendingTasks", structuralDiscrepancies);
        return "comms-review";
    }

    @PostMapping("/sync-now")
    public String triggerManualCalculationSweep() {
        syncEngineService.runTwoWayReconciliationSync();
        return "redirect:/comms/review";
    }
    
    @PostMapping("/approve")
    public String approveTask(
            @RequestParam("studentName") String studentName,
            @RequestParam("email") String email,
            @RequestParam("viciAccountId") String viciAccountId,
            @RequestParam("actionType") String actionType) {

        System.out.println("Processing dynamic CRM override adjustments for student: " + studentName);

        // Build your flat CRM array logic pipeline loops here based on actionType matching:
        // Type A ("Approve & Sync") updates Brevo parallel status flag to LAPSED and issues reminder email template.
        // Type B ("Clear Flag") updates Brevo parallel status to ACTIVE silently, without issuing email.
        
        // After syncing with Brevo, remove or flag the resolved record out of your ledger
        alertStudentRepository.deleteById(studentName);

        return "redirect:/comms/review";
    }
}