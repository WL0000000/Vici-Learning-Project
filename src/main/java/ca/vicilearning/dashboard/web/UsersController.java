package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.auth.AppUser;
import ca.vicilearning.dashboard.auth.AppUserRepository;
import ca.vicilearning.dashboard.auth.Role;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.List;

@Controller
public class UsersController {

    private final AppUserRepository users;

    public UsersController(AppUserRepository users) {
        this.users = users;
    }

    @GetMapping("/admin/users")
    public String userManagement(Model model) {
        List<AppUser> pending = users.findByApprovedFalse();
        List<AppUser> everyone = users.findAll().stream()
                .sorted(Comparator.comparing(AppUser::getUsername, String.CASE_INSENSITIVE_ORDER))
                .toList();

        model.addAttribute("pendingUsers", pending);
        model.addAttribute("allUsers", everyone);
        model.addAttribute("roles", Role.values());
        return "dashboard-users";
    }

    @PostMapping("/admin/users/{id}/approve")
    public String approve(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        users.findById(id).ifPresent(u -> {
            u.setApproved(true);
            users.save(u);
        });
        redirectAttributes.addFlashAttribute("successMessage", "Account approved.");
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/users/{id}/reject")
    public String reject(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        users.deleteById(id);
        redirectAttributes.addFlashAttribute("successMessage", "Request rejected and removed.");
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/users/{id}/role")
    public String changeRole(@PathVariable Long id, @RequestParam Role role,
                             RedirectAttributes redirectAttributes) {
        users.findById(id).ifPresent(u -> {
            u.setRole(role);
            users.save(u);
        });
        redirectAttributes.addFlashAttribute("successMessage", "Role updated.");
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/users/{id}/toggle-access")
    public String toggleAccess(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        users.findById(id).ifPresent(u -> {
            u.setApproved(!u.isApproved());
            users.save(u);
        });
        redirectAttributes.addFlashAttribute("successMessage", "Access updated.");
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/users/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        users.deleteById(id);
        redirectAttributes.addFlashAttribute("successMessage", "Account deleted.");
        return "redirect:/admin/users";
    }
}