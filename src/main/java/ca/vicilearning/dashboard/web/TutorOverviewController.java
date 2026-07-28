package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.domain.Tutor;
import ca.vicilearning.dashboard.metrics.DashboardMetricsService.UpcomingSession;
import ca.vicilearning.dashboard.tutorportal.TutorPortalDataService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.List;

@Controller
public class TutorOverviewController {

    private final TutorPortalDataService data;

    public TutorOverviewController(TutorPortalDataService data) {
        this.data = data;
    }

    @GetMapping("/tutor-portal/overview")
    public String overview(Model model, Authentication auth) {
        var tutorOpt = data.resolveTutor(auth.getName());

        if (tutorOpt.isEmpty()) {
            model.addAttribute("tutorLinked", false);
            return "tutor-overview";
        }

        Tutor tutor = tutorOpt.get();
        model.addAttribute("tutorLinked", true);
        model.addAttribute("tutorName", tutor.getName());

        LocalDate today = data.today();
        LocalDate weekStart = data.weekStart(today);
        TutorPortalDataService.WeekStats stats = data.weekStats(tutor, weekStart);

        model.addAttribute("sessionsThisWeek", stats.sessionsThisWeek());
        model.addAttribute("hoursThisWeek", stats.hoursThisWeek());
        model.addAttribute("studentCount", data.myStudentSummaries(tutor).size());
        model.addAttribute("avgSessionsPerStudent", data.avgSessionsPerStudent(tutor));

        List<UpcomingSession> todaysSessions = stats.sessions().stream()
                .filter(s -> s.startTime().toLocalDate().equals(today))
                .toList();
        model.addAttribute("todaysSessions", todaysSessions);

        return "tutor-overview";
    }
}