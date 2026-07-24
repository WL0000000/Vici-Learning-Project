// TODO: COMPLETE THIS
package ca.vicilearning.dashboard.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

public class UsersController {
    
    @GetMapping("/admin/users")
    public String userManagement(Model model) {

        // TODO:
        // model.addAttribute("users", userService.findAll());

        return "dashboard-users";
    }

}
