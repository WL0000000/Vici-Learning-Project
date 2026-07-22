package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.notion.NotionTutor;
import ca.vicilearning.dashboard.notion.NotionService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/api/notion")
public class NotionController {

    private final NotionService notionService;

    public NotionController(NotionService notionService) {
        this.notionService = notionService;
    }

    @GetMapping("/tutors")
    public String tutors(@RequestParam(defaultValue = "") String q,
                         @RequestParam(defaultValue = "active") String sort,
                         Model model) {
        List<NotionTutor> allTutors = notionService.getTutorRows();
        List<NotionTutor> tutors = allTutors.stream()
                .filter(tutor -> matchesQuery(tutor, q))
                .sorted(sortComparator(sort))
                .toList();

        model.addAttribute("tutors", tutors);
        model.addAttribute("totalTutorCount", allTutors.size());
        model.addAttribute("activeTutorCount", countByStatus(allTutors, "Active"));
        model.addAttribute("inactiveTutorCount", countByStatus(allTutors, "Inactive"));
        model.addAttribute("query", q);
        model.addAttribute("sort", sort);
        return "notion-tutors";
    }

    @PostMapping("/tutors/{pageId}")
    public String updateTutor(@PathVariable String pageId,
                              @RequestParam Map<String, String> formValues,
                              RedirectAttributes redirectAttributes) {
        try {
            notionService.updateTutor(pageId, formValues);
            redirectAttributes.addFlashAttribute("successMessage", "Tutor saved to Notion.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Could not save tutor: " + e.getMessage());
        }
        return "redirect:/api/notion/tutors";
    }

    @ResponseBody
    @GetMapping(value = "/tutors/raw", produces = MediaType.APPLICATION_JSON_VALUE)
    public String rawTutors() {
        return notionService.getTutors();
    }

    private boolean matchesQuery(NotionTutor tutor, String query) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return true;
        }
        return normalize(tutor.name()).contains(normalizedQuery)
                || normalize(tutor.subjects()).contains(normalizedQuery)
                || normalize(tutor.email()).contains(normalizedQuery);
    }

    private Comparator<NotionTutor> sortComparator(String sort) {
        return switch (sort) {
            case "name" -> comparingText(NotionTutor::name)
                    .thenComparingInt(tutor -> statusRank(tutor.status()));
            case "subject" -> comparingText(NotionTutor::subjects)
                    .thenComparing(comparingText(NotionTutor::name));
            case "status" -> Comparator.comparingInt((NotionTutor tutor) -> statusRank(tutor.status()))
                    .thenComparing(comparingText(NotionTutor::name));
            default -> Comparator.comparingInt((NotionTutor tutor) -> statusRank(tutor.status()))
                    .thenComparing(comparingText(NotionTutor::name));
        };
    }

    private Comparator<NotionTutor> comparingText(java.util.function.Function<NotionTutor, String> accessor) {
        return Comparator.comparing(tutor -> normalize(accessor.apply(tutor)));
    }

    private long countByStatus(List<NotionTutor> tutors, String status) {
        return tutors.stream()
                .filter(tutor -> status.equalsIgnoreCase(tutor.status()))
                .count();
    }

    private int statusRank(String status) {
        if ("Active".equalsIgnoreCase(status)) {
            return 0;
        }
        if ("Not started".equalsIgnoreCase(status)) {
            return 1;
        }
        if ("Inactive".equalsIgnoreCase(status)) {
            return 2;
        }
        return 3;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
