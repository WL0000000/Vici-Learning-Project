package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.metrics.DashboardMetricsService.UpcomingSession;
import ca.vicilearning.dashboard.tutorportal.TutorPortalSampleData;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** landing page of the tutor portal */
@Controller
public class TutorOverviewController {

    private final TutorPortalSampleData data;

    public TutorOverviewController(TutorPortalSampleData data) {
        this.data = data;
    }

    @GetMapping("/tutor-portal/overview")
    public String overview(Model model) {
        model.addAttribute("tutorName", data.tutorName());
        model.addAttribute("sessionsThisWeek", data.sessionsThisWeek());
        model.addAttribute("hoursThisWeek", data.hoursThisWeek());
        model.addAttribute("studentCount", data.studentCount());

        LocalDate today = LocalDate.now();
        List<UpcomingSession> todaysSessions = data.myUpcoming().stream()
                .filter(s -> s.startTime().toLocalDate().equals(today))
                .sorted(Comparator.comparing(UpcomingSession::startTime))
                .toList();
        model.addAttribute("todaysSessions", todaysSessions);

        return "tutor-overview";
    }
}