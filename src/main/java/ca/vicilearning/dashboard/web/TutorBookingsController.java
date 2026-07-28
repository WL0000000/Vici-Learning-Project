package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.domain.Tutor;
import ca.vicilearning.dashboard.metrics.DashboardMetricsService.UpcomingSession;
import ca.vicilearning.dashboard.tutorportal.TutorPortalDataService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Controller
public class TutorBookingsController {

    private final TutorPortalDataService data;

    public TutorBookingsController(TutorPortalDataService data) {
        this.data = data;
    }

    @GetMapping("/tutor-portal/bookings")
    public String bookings(@RequestParam(defaultValue = "week") String view, Model model, Authentication auth) {
        var tutorOpt = data.resolveTutor(auth.getName());

        if (tutorOpt.isEmpty()) {
            model.addAttribute("tutorLinked", false);
            return "tutor-bookings";
        }

        Tutor tutor = tutorOpt.get();
        model.addAttribute("tutorLinked", true);
        model.addAttribute("tutorName", tutor.getName());
        model.addAttribute("view", view);

        if ("extended".equals(view)) {
            model.addAttribute("weekGroups", data.upcomingWeekGroups(tutor, 8));
        } else {
            LocalDate today = data.today();
            LocalDate weekStart = data.weekStart(today);
            TutorPortalDataService.WeekStats stats = data.weekStats(tutor, weekStart);

            List<WeekDay> weekDays = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                LocalDate day = weekStart.plusDays(i);
                List<UpcomingSession> sessionsThatDay = stats.sessions().stream()
                        .filter(s -> s.startTime().toLocalDate().equals(day))
                        .toList();
                weekDays.add(new WeekDay(
                        day.format(DateTimeFormatter.ofPattern("EEE")).toUpperCase(),
                        day.format(DateTimeFormatter.ofPattern("d")),
                        day.equals(today),
                        sessionsThatDay
                ));
            }
            model.addAttribute("weekDays", weekDays);
            model.addAttribute("weekRangeLabel",
                    weekStart.format(DateTimeFormatter.ofPattern("MMM d")) + " - "
                            + weekStart.plusDays(6).format(DateTimeFormatter.ofPattern("MMM d")));
        }

        return "tutor-bookings";
    }

    public record WeekDay(String dayLabel, String dateNum, boolean isToday, List<UpcomingSession> sessions) {}
}