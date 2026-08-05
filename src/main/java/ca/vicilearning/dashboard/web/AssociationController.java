package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.association.AssociationService;
import ca.vicilearning.dashboard.metrics.DashboardMetricsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Association Account page (Meeting #3, Sara's #1 feature): shows families (students grouped by
 * their assigned Account_ID) and the queue of unassigned students, lets staff assign/move/rename/merge
 * families, and drills into each family to see its individual kids + the family's activity rollup.
 * Reads/writes only the local DB.
 */
@Controller
public class AssociationController {

    private final AssociationService associations;
    private final DashboardMetricsService metrics;

    public AssociationController(AssociationService associations, DashboardMetricsService metrics) {
        this.associations = associations;
        this.metrics = metrics;
    }

    @GetMapping("/associations")
    public String associations(Model model) {
        model.addAttribute("families", associations.families());
        model.addAttribute("unassigned", associations.unassignedStudents());
        model.addAttribute("familyKeys", associations.existingFamilyKeys());
        model.addAttribute("emptyFamilies", associations.emptyFamilies());
        // Per-family activity (this week's hours/sessions, categories/locations, memberships) for the
        // drill-down, keyed by Account_ID. Covers all families (incl. single-kid), unlike familyGroups.
        model.addAttribute("familyActivity", metrics.familyActivityByAccount(null));
        return "associations";
    }

    @PostMapping("/associations/assign")
    public String assign(@RequestParam String extId, @RequestParam String accountId) {
        associations.assignToFamily(extId, accountId);
        return "redirect:/associations";
    }

    @PostMapping("/associations/family")
    public String updateFamily(@RequestParam String accountId,
                               @RequestParam(required = false) String name,
                               @RequestParam(required = false) String notes) {
        associations.updateFamily(accountId, name, notes);
        return "redirect:/associations";
    }

    @PostMapping("/associations/unassign")
    public String unassign(@RequestParam String extId) {
        associations.unassign(extId);
        return "redirect:/associations";
    }

    @PostMapping("/associations/rename")
    public String rename(@RequestParam String accountId, @RequestParam String newAccountId) {
        associations.renameFamily(accountId, newAccountId);
        return "redirect:/associations";
    }

    @PostMapping("/associations/merge")
    public String merge(@RequestParam String fromAccountId, @RequestParam String intoAccountId) {
        associations.mergeFamilies(fromAccountId, intoAccountId);
        return "redirect:/associations";
    }

    @PostMapping("/associations/family/delete")
    public String deleteFamily(@RequestParam String accountId) {
        associations.deleteFamily(accountId);
        return "redirect:/associations";
    }
}
