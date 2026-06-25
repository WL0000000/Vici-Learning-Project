package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.metrics.DashboardMetricsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Read-only page that renders live (synced or seeded) data: overview metrics, computed weekly
 * hours, the per-student Brevo+SimplyBook view, upcoming sessions, and per-tutor totals.
 *
 * <p>Deliberately a separate route from {@code /} (the Overview page owned by the frontend
 * branch) so this data-backed view can ship without colliding with that in-progress template.
 */
@Controller
public class StudentsController {

    private final DashboardMetricsService metrics;

    public StudentsController(DashboardMetricsService metrics) {
        this.metrics = metrics;
    }

    private static final int WEEKS_BACK = 3;
    private static final int WEEKS_AHEAD = 2;
    private static final DateTimeFormatter WEEK_LABEL = DateTimeFormatter.ofPattern("MMM d");

    @GetMapping("/students")
    public String students(Model model) {
        model.addAttribute("overview", metrics.overview());

        // 3 weeks of history + this week + 2 ahead — covers the "2 weeks ahead" client ask.
        List<DashboardMetricsService.WeeklyHours> weeklyHours = metrics.weeklyHours(WEEKS_BACK, WEEKS_AHEAD);
        model.addAttribute("weeklyHours", weeklyHours);

        // Chart-ready arrays. Thymeleaf serializes these lists to JSON for Chart.js.
        model.addAttribute("weekLabels",
                weeklyHours.stream().map(w -> w.weekStart().format(WEEK_LABEL)).toList());
        model.addAttribute("weekHours",
                weeklyHours.stream().map(DashboardMetricsService.WeeklyHours::hours).toList());
        model.addAttribute("weekSessions",
                weeklyHours.stream().map(DashboardMetricsService.WeeklyHours::sessions).toList());
        // Index of the current week in the arrays (weeks before it are history, after it are upcoming).
        model.addAttribute("currentWeekIndex", WEEKS_BACK);

        model.addAttribute("students", metrics.studentRows());
        model.addAttribute("upcoming", metrics.upcoming(10));
        model.addAttribute("tutorHours", metrics.tutorHoursThisWeek());
        return "students";
    }
}
