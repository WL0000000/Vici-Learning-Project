package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.domain.SyncLog;
import ca.vicilearning.dashboard.domain.SyncLogRepository;
import ca.vicilearning.dashboard.metrics.DashboardMetricsService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Controller
public class HomeController {

    private final DashboardMetricsService metrics;
    private final SyncLogRepository syncLogRepo;

    public HomeController(DashboardMetricsService metrics, SyncLogRepository syncLogRepo) {
        this.metrics = metrics;
        this.syncLogRepo = syncLogRepo;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("overview", metrics.overview());
        model.addAttribute("upcoming", metrics.upcoming(6));
        model.addAttribute("actionItems", metrics.actionRequired());
        model.addAttribute("invoiceSummary", metrics.pendingInvoicesSummary());
        model.addAttribute("pendingInvoices", metrics.pendingInvoices(8));

        // same logic as SyncController, just need the most recent log to show on the homepage strip
        List<SyncLog> logs = syncLogRepo.findByOrderByStartedAtDesc(PageRequest.of(0, 1));
        SyncLog last = logs.isEmpty() ? null : logs.get(0);
        Long minutesAgo = last == null ? null
                : ChronoUnit.MINUTES.between(last.getStartedAt(), LocalDateTime.now(ZoneOffset.UTC));
        model.addAttribute("minutesAgo", minutesAgo);

        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}