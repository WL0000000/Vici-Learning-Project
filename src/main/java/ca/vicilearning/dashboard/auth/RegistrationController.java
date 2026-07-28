package ca.vicilearning.dashboard.auth;

import ca.vicilearning.dashboard.comms.BrevoCommunicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RegistrationController {

    private static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";

    private final AppUserService users;
    private final BrevoCommunicationService brevo;

    @Value("${ADMIN_NOTIFICATION_EMAIL:}")
    private String adminNotificationEmail;

    @Value("${BREVO_TUTOR_APPROVAL_TEMPLATE_ID:0}")
    private long tutorApprovalTemplateId;

    public RegistrationController(AppUserService users, BrevoCommunicationService brevo) {
        this.users = users;
        this.brevo = brevo;
    }

    @GetMapping("/register")
    public String showForm() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String password,
                           @RequestParam String confirmPassword,
                           Model model) {
        model.addAttribute("username", username);

        if (username == null || !username.trim().matches(EMAIL_PATTERN)) {
            model.addAttribute("error", "Please use your email address, not a username.");
            return "register";
        }

        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            return "register";
        }

        AppUserService.RegistrationResult result;
        try {
            result = users.registerSelfService(username, password);
        } catch (DuplicateUsernameException e) {
            model.addAttribute("error", "That email is already registered.");
            return "register";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }

        // Senior tutors get auto-approved via Notion and can sign in right away
        if (result.pendingApproval()) {
            brevo.notifyAdminOfPendingTutor(adminNotificationEmail, tutorApprovalTemplateId, username);
            return "redirect:/login?pending";
        }
        return "redirect:/login?registered";
    }
}