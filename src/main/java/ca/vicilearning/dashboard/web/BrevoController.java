package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.comms.AutomationsQueueService;
import ca.vicilearning.dashboard.comms.BrevoCommunicationService;
import ca.vicilearning.dashboard.domain.AlertStudent;
import ca.vicilearning.dashboard.domain.AlertStudentRepository;
import ca.vicilearning.dashboard.domain.Student;
import ca.vicilearning.dashboard.domain.StudentRepository;
import ca.vicilearning.dashboard.sync.BrevoSyncEngineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    // Shown when Student.email is blank in SimplyBook.me itself, not a Brevo lookup failure.
    // This page doesn't look up Brevo for email at all anymore, see AlertStudent.email above.
    private static final String NOT_FOUND_FALLBACK = "No email on file";
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
    private final AutomationsQueueService queueService;

    // Template IDs for the newer email types, need to be created in Sara's Brevo account first.
    // Bound as String, not long: Spring's @Value("...:0") default only kicks in when the key is
    // missing entirely, and .env.example ships these blank (present but empty), which would throw
    // trying to parse "" as a long. parseTemplateId() handles both cases and just returns 0. Each
    // payment reminder tier gets its own template since the tone should be different for a
    // 2-week nudge vs a same-day warning.
    @Value("${BREVO_RENEWAL_REMINDER_TEMPLATE_ID:}")
    private String renewalReminderTemplateIdRaw;

    @Value("${BREVO_PAYMENT_REMINDER_2WK_TEMPLATE_ID:}")
    private String paymentReminder2wkTemplateIdRaw;

    @Value("${BREVO_PAYMENT_REMINDER_72H_TEMPLATE_ID:}")
    private String paymentReminder72hTemplateIdRaw;

    @Value("${BREVO_PAYMENT_REMINDER_12H_TEMPLATE_ID:}")
    private String paymentReminder12hTemplateIdRaw;

    /** Blank or unset resolves to 0 ("not configured"); anything else must parse as a valid id. */
    private long parseTemplateId(String raw) {
        return (raw == null || raw.isBlank()) ? 0L : Long.parseLong(raw.trim());
    }

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
                           BrevoSyncEngineService syncEngineService,
                           AutomationsQueueService queueService) {
        this.communicationService = communicationService;
        this.alertStudentRepository = alertStudentRepository;
        this.studentRepository = studentRepository;
        this.syncEngineService = syncEngineService;
        this.queueService = queueService;
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

        if (localAlerts != null) {
            for (AlertStudent alert : localAlerts) {
                String displayEmail = (alert.getEmail() == null || alert.getEmail().isBlank())
                        ? NOT_FOUND_FALLBACK : alert.getEmail().trim();

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
        model.addAttribute("renewalTasks", queueService.renewalQueue());
        model.addAttribute("paymentReminderTasks", queueService.paymentReminderQueue());
        model.addAttribute("renewalTemplateConfigured", parseTemplateId(renewalReminderTemplateIdRaw) > 0);
        model.addAttribute("payment2wkTemplateConfigured", parseTemplateId(paymentReminder2wkTemplateIdRaw) > 0);
        model.addAttribute("payment72hTemplateConfigured", parseTemplateId(paymentReminder72hTemplateIdRaw) > 0);
        model.addAttribute("payment12hTemplateConfigured", parseTemplateId(paymentReminder12hTemplateIdRaw) > 0);
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

    // sends the renewal reminder and marks it sent so the membership doesn't show back up on
    // the queue right away (see AutomationsQueueService's cooldown)
    @PostMapping("/approve-renewal")
    public String approveRenewal(@RequestParam("membershipId") Long membershipId,
                                 @RequestParam("studentName") String studentName,
                                 @RequestParam("email") String email,
                                 @RequestParam(value = "remainingCount", required = false) Integer remainingCount) {
        log.info("Processing renewal follow-up approval for student: {}", studentName);
        try {
            if (email == null || email.isBlank()) {
                return REDIRECT_REVIEW_ERR_EMAIL;
            }
            long templateId = parseTemplateId(renewalReminderTemplateIdRaw);
            if (templateId <= 0) {
                log.warn("Renewal reminder template not configured; skipping send for {}", studentName);
                return REDIRECT_REVIEW;
            }
            Map<String, Object> params = Map.of(
                    "STUDENT_NAME", studentName,
                    "SESSIONS_LEFT", remainingCount == null ? "" : remainingCount.toString()
            );
            communicationService.sendTemplatedEmail(email, studentName, templateId, params);
            queueService.markRenewalReminderSent(membershipId);
        } catch (Exception e) {
            log.error("Error sending renewal reminder for: {}", studentName, e);
        }
        return REDIRECT_REVIEW;
    }

    // sends the payment reminder for whichever tier triggered it, and records the tier so it
    // doesn't get re-sent, though a more urgent tier later can still go out
    @PostMapping("/approve-payment-reminder")
    public String approvePaymentReminder(@RequestParam("invoiceId") Long invoiceId,
                                         @RequestParam("studentName") String studentName,
                                         @RequestParam("email") String email,
                                         @RequestParam("urgency") String urgency,
                                         @RequestParam(value = "invoiceNumber", required = false) String invoiceNumber) {
        log.info("Processing payment reminder approval for student: {} ({})", studentName, urgency);
        try {
            if (email == null || email.isBlank()) {
                return REDIRECT_REVIEW_ERR_EMAIL;
            }
            AutomationsQueueService.Urgency parsedUrgency = AutomationsQueueService.Urgency.valueOf(urgency);
            long templateId = templateIdFor(parsedUrgency);
            if (templateId <= 0) {
                log.warn("Payment reminder template not configured for tier {}; skipping send for {}", urgency, studentName);
                return REDIRECT_REVIEW;
            }
            Map<String, Object> params = Map.of(
                    "STUDENT_NAME", studentName,
                    "INVOICE_NUMBER", invoiceNumber == null ? "" : invoiceNumber
            );
            communicationService.sendTemplatedEmail(email, studentName, templateId, params);
            queueService.markPaymentReminderSent(invoiceId, parsedUrgency);
        } catch (Exception e) {
            log.error("Error sending payment reminder for: {}", studentName, e);
        }
        return REDIRECT_REVIEW;
    }

    /** Which Brevo template corresponds to a given payment-reminder urgency tier. */
    private long templateIdFor(AutomationsQueueService.Urgency urgency) {
        return switch (urgency) {
            case REMINDER_2WK -> parseTemplateId(paymentReminder2wkTemplateIdRaw);
            case REMINDER_72H -> parseTemplateId(paymentReminder72hTemplateIdRaw);
            case URGENT_12H -> parseTemplateId(paymentReminder12hTemplateIdRaw);
        };
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