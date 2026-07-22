package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.tutorportal.TutorPortalSampleData;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** just the tutor's own students, basic contact info */
@Controller
public class TutorStudentsController {

    private final TutorPortalSampleData data;

    public TutorStudentsController(TutorPortalSampleData data) {
        this.data = data;
    }

    @GetMapping("/tutor-portal/students")
    public String students(Model model) {
        model.addAttribute("tutorName", data.tutorName());
        model.addAttribute("myStudents", data.myStudents());
        return "tutor-students";
    }
}