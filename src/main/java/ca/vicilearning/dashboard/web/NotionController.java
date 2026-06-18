package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.notion.NotionService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/api/notion")
public class NotionController {

    private final NotionService notionService;

    public NotionController(NotionService notionService) {
        this.notionService = notionService;
    }

    @GetMapping("/tutors")
    public String tutors(Model model) {
        model.addAttribute("tutors", notionService.getTutorRows());
        return "notion-tutors";
    }

    @ResponseBody
    @GetMapping(value = "/tutors/raw", produces = MediaType.APPLICATION_JSON_VALUE)
    public String rawTutors() {
        return notionService.getTutors();
    }
}
