package ca.vicilearning.dashboard.auth;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Serves the registration page and handles sign-ups. On success the new user is sent to the
 * login page; on any validation problem the form is re-rendered with an error and the entered
 * username preserved (never the password).
 */
@Controller
public class RegistrationController {

    private final AppUserService users;

    public RegistrationController(AppUserService users) {
        this.users = users;
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

        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            return "register";
        }
        try {
            users.register(username, password);
        } catch (DuplicateUsernameException e) {
            model.addAttribute("error", "That username is already taken.");
            return "register";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
        // Success → login page shows a "registered" confirmation banner.
        return "redirect:/login?registered";
    }
}
