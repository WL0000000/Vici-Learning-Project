package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.association.AssociationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Association Account page (Meeting #3, Sara's #1 feature): shows families (students grouped by
 * their assigned Account_ID) and the queue of unassigned students, and lets staff assign a
 * student to a family. Reads/writes only the local DB.
 */
@Controller
public class AssociationController {

    private final AssociationService associations;

    public AssociationController(AssociationService associations) {
        this.associations = associations;
    }

    @GetMapping("/associations")
    public String associations(Model model) {
        model.addAttribute("families", associations.families());
        model.addAttribute("unassigned", associations.unassignedStudents());
        model.addAttribute("familyKeys", associations.existingFamilyKeys());
        return "associations";
    }

    @PostMapping("/associations/assign")
    public String assign(@RequestParam Long studentId, @RequestParam String accountId) {
        associations.assignToFamily(studentId, accountId);
        return "redirect:/associations";
    }
}
