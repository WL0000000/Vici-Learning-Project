package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.domain.Tutor;
import ca.vicilearning.dashboard.metrics.DashboardMetricsService.UpcomingSession;
import ca.vicilearning.dashboard.tutorportal.TutorPortalDataService;
import ca.vicilearning.dashboard.tutorportal.TutorPortalDataService.StatsPeriod;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
public class TutorOverviewController {

    private final TutorPortalDataService data;

    public TutorOverviewController(TutorPortalDataService data) {
        this.data = data;
    }

    @GetMapping("/tutor-portal/overview")
    public String overview(@RequestParam(defaultValue = "week") String avgPeriod, Model model, Authentication auth) {
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

        // "overall for the tutor" stat, now filterable by period per Sara's follow-up feedback
        StatsPeriod period = switch (avgPeriod) {
            case "month" -> StatsPeriod.MONTH;
            case "all" -> StatsPeriod.ALL_TIME;
            default -> StatsPeriod.WEEK;
        };
        model.addAttribute("avgPeriod", avgPeriod);
        model.addAttribute("avgSessionsPerStudent", data.avgSessionsPerStudent(tutor, period));

        List<UpcomingSession> todaysSessions = stats.sessions().stream()
                .filter(s -> s.startTime().toLocalDate().equals(today))
                .toList();
        model.addAttribute("todaysSessions", todaysSessions);

        return "tutor-overview";
    }
}