package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.metrics.DashboardMetricsService.UpcomingSession;
import ca.vicilearning.dashboard.tutorportal.TutorPortalSampleData;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// read-only weekly calendar of the tutor's own bookings on purpose 
@Controller
public class TutorBookingsController {

    private final TutorPortalSampleData data;

    public TutorBookingsController(TutorPortalSampleData data) {
        this.data = data;
    }

    @GetMapping("/tutor-portal/bookings")
    public String bookings(Model model) {
        model.addAttribute("tutorName", data.tutorName());

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<UpcomingSession> myUpcoming = data.myUpcoming();

        List<WeekDay> weekDays = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = weekStart.plusDays(i);
            List<UpcomingSession> sessionsThatDay = myUpcoming.stream()
                    .filter(s -> s.startTime().toLocalDate().equals(day))
                    .sorted(Comparator.comparing(UpcomingSession::startTime))
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

        return "tutor-bookings";
    }

    public record WeekDay(String dayLabel, String dateNum, boolean isToday, List<UpcomingSession> sessions) {}
}