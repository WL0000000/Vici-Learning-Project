package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.domain.Tutor;
import ca.vicilearning.dashboard.tutorportal.TutorPortalDataService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TutorStudentsController {

    private final TutorPortalDataService data;

    public TutorStudentsController(TutorPortalDataService data) {
        this.data = data;
    }

    @GetMapping("/tutor-portal/students")
    public String students(Model model, Authentication auth) {
        var tutorOpt = data.resolveTutor(auth.getName());

        if (tutorOpt.isEmpty()) {
            model.addAttribute("tutorLinked", false);
            return "tutor-students";
        }

        Tutor tutor = tutorOpt.get();
        model.addAttribute("tutorLinked", true);
        model.addAttribute("tutorName", tutor.getName());
        model.addAttribute("myStudents", data.myStudentSummaries(tutor));

        return "tutor-students";
    }
}