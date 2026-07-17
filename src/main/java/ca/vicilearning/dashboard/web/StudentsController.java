package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.domain.StudentStatus;
import ca.vicilearning.dashboard.metrics.DashboardMetricsService;
import ca.vicilearning.dashboard.metrics.DashboardMetricsService.PeriodUnit;
import ca.vicilearning.dashboard.metrics.DashboardMetricsService.ServiceScope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Read-only page that renders live (synced or seeded) data: overview metrics, computed
 * week/month/year (or custom date range) hours, the per-student Brevo+SimplyBook view, upcoming
 * sessions, and per-tutor totals — this is where Sara's "filter by week/month/year/date range,
 * sort by tutor and total" ask lives.
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

    // Default look-back/look-ahead per bucket granularity, chosen so each chart shows a
    // reasonable timeline without the client having to configure anything.
    private static final int WEEKS_BACK = 3, WEEKS_AHEAD = 2;
    private static final int MONTHS_BACK = 3, MONTHS_AHEAD = 1;
    private static final int YEARS_BACK = 2, YEARS_AHEAD = 0;

    @GetMapping("/students")
    public String students(
            @RequestParam(defaultValue = "week") String unit,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "hours") String sort,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            Model model) {

        boolean sortByName = "name".equalsIgnoreCase(sort);
        model.addAttribute("selectedSort", sortByName ? "name" : "hours");

        // Enrolment-status filter for the roster (Meeting #4): ACTIVE / PAUSED / all. Null = all.
        // Exposed so the dropdown and the other filter links can preserve the selection.
        StudentStatus statusFilter = parseStatus(status);
        model.addAttribute("selectedStatus", statusFilter != null ? statusFilter.name() : null);
        model.addAttribute("studentStatuses", StudentStatus.values());

        // Two independent service filters (Meeting #4): location (delivery mode) and category
        // (session type). Blank means "any". Both dropdowns' options + current selections are
        // exposed so the view can preserve each across the other filter links.
        String locationFilter = blankToNull(location);
        String categoryFilter = blankToNull(category);
        ServiceScope scope = (locationFilter == null && categoryFilter == null)
                ? null : new ServiceScope(locationFilter, categoryFilter);
        model.addAttribute("serviceLocations", metrics.serviceLocations());
        model.addAttribute("serviceCategories", metrics.serviceCategories());
        model.addAttribute("selectedLocation", locationFilter);
        model.addAttribute("selectedCategory", categoryFilter);
        // One human-readable label combining whichever filters are active (null when none), so the
        // "filtered: …" indicators on each section can render without repeating the logic.
        model.addAttribute("filterLabel", filterLabel(locationFilter, categoryFilter));

        // Overview cards honour the same filter, so the whole page reflects the chosen scope.
        model.addAttribute("overview", metrics.overview(scope));

        boolean customRange = from != null && !from.isBlank() && to != null && !to.isBlank();
        model.addAttribute("customRange", customRange);

        if (customRange) {
            LocalDate fromDate = LocalDate.parse(from);
            LocalDate toDate = LocalDate.parse(to);
            model.addAttribute("selectedUnit", "range");
            model.addAttribute("panelTitle", "Hours Booked (Date Range)");
            model.addAttribute("rangeFrom", fromDate);
            model.addAttribute("rangeTo", toDate);
            model.addAttribute("rangeHours", metrics.hoursInRange(fromDate, toDate.plusDays(1), scope));
            model.addAttribute("periodSubtitle",
                    fromDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")) + " – "
                            + toDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")));
            model.addAttribute("tutorHours",
                    metrics.tutorHoursForRange(fromDate, toDate.plusDays(1), sortByName, scope));
            model.addAttribute("tutorPeriodLabel", "selected range");

            // No bucket chart in custom-range mode — keep these present (empty) so the
            // inline Chart.js script below has well-defined arrays to read.
            model.addAttribute("periodLabels", List.of());
            model.addAttribute("periodHoursData", List.of());
            model.addAttribute("periodSessions", List.of());
            model.addAttribute("currentPeriodIndex", 0);
        } else {
            PeriodUnit periodUnit = parseUnit(unit);
            String unitLabel = periodUnit.name().toLowerCase();
            model.addAttribute("selectedUnit", unitLabel);
            model.addAttribute("panelTitle",
                    "Hours Booked by " + Character.toUpperCase(unitLabel.charAt(0)) + unitLabel.substring(1));

            int back = switch (periodUnit) {
                case WEEK -> WEEKS_BACK;
                case MONTH -> MONTHS_BACK;
                case YEAR -> YEARS_BACK;
            };
            int ahead = switch (periodUnit) {
                case WEEK -> WEEKS_AHEAD;
                case MONTH -> MONTHS_AHEAD;
                case YEAR -> YEARS_AHEAD;
            };

            List<DashboardMetricsService.PeriodHours> buckets = metrics.hoursByPeriod(periodUnit, back, ahead, scope);
            DateTimeFormatter labelFmt = labelFormat(periodUnit);

            model.addAttribute("periodLabels",
                    buckets.stream().map(p -> p.periodStart().format(labelFmt)).toList());
            model.addAttribute("periodHoursData",
                    buckets.stream().map(DashboardMetricsService.PeriodHours::hours).toList());
            model.addAttribute("periodSessions",
                    buckets.stream().map(DashboardMetricsService.PeriodHours::sessions).toList());
            // Index of the current bucket in the arrays (buckets before it are history, after are upcoming).
            model.addAttribute("currentPeriodIndex", back);
            model.addAttribute("periodSubtitle", switch (periodUnit) {
                case WEEK -> WEEKS_BACK + " weeks back · this week · " + WEEKS_AHEAD + " ahead";
                case MONTH -> MONTHS_BACK + " months back · this month · " + MONTHS_AHEAD + " ahead";
                case YEAR -> YEARS_BACK + " years back · this year";
            });

            model.addAttribute("tutorHours", metrics.tutorHoursForPeriod(periodUnit, sortByName, scope));
            model.addAttribute("tutorPeriodLabel", switch (periodUnit) {
                case WEEK -> "this week";
                case MONTH -> "this month";
                case YEAR -> "this year";
            });
        }

        model.addAttribute("students", metrics.studentRows(scope, statusFilter));
        model.addAttribute("families", metrics.familyGroups(scope));
        model.addAttribute("upcoming", metrics.upcoming(10, scope));
        return "students";
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** Parse the status filter param to a {@link StudentStatus}, or null for "all"/unrecognized. */
    private static StudentStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return StudentStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** "Location · Category" when both set, one when only one is, null when neither. */
    private static String filterLabel(String location, String category) {
        if (location != null && category != null) return location + " · " + category;
        return location != null ? location : category;
    }

    private PeriodUnit parseUnit(String unit) {
        try {
            return PeriodUnit.valueOf(unit.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PeriodUnit.WEEK;
        }
    }

    private DateTimeFormatter labelFormat(PeriodUnit unit) {
        return switch (unit) {
            case WEEK -> DateTimeFormatter.ofPattern("MMM d");
            case MONTH -> DateTimeFormatter.ofPattern("MMM yyyy");
            case YEAR -> DateTimeFormatter.ofPattern("yyyy");
        };
    }
}
