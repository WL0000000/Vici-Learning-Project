package ca.vicilearning.dashboard.web;

import ca.vicilearning.dashboard.domain.*;
import ca.vicilearning.dashboard.sync.SyncService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Controller
@RequestMapping("/sync")
public class SyncController {

    private final SyncService        syncService;
    private final SyncLogRepository  syncLogRepo;
    private final StudentRepository  studentRepo;
    private final TutorRepository    tutorRepo;
    private final ServiceRepository  serviceRepo;
    private final BookingRepository  bookingRepo;

    public SyncController(SyncService syncService,
                          SyncLogRepository syncLogRepo,
                          StudentRepository studentRepo,
                          TutorRepository tutorRepo,
                          ServiceRepository serviceRepo,
                          BookingRepository bookingRepo) {
        this.syncService  = syncService;
        this.syncLogRepo  = syncLogRepo;
        this.studentRepo  = studentRepo;
        this.tutorRepo    = tutorRepo;
        this.serviceRepo  = serviceRepo;
        this.bookingRepo  = bookingRepo;
    }

    @GetMapping
    public String page(Model model) {
        List<SyncLog> logs = syncLogRepo.findByOrderByStartedAtDesc(PageRequest.of(0, 5));

        SyncLog last = logs.isEmpty() ? null : logs.get(0);
        Long minutesAgo = last == null ? null
                : ChronoUnit.MINUTES.between(last.getStartedAt(), LocalDateTime.now(ZoneOffset.UTC));

        model.addAttribute("recentLogs",   logs);
        model.addAttribute("lastSync",     last);
        model.addAttribute("minutesAgo",   minutesAgo);
        model.addAttribute("studentCount", studentRepo.count());
        model.addAttribute("tutorCount",   tutorRepo.count());
        model.addAttribute("serviceCount", serviceRepo.count());
        model.addAttribute("bookingCount", bookingRepo.count());

        return "sync";
    }

    @PostMapping("/now")
    public String syncNow() {
        syncService.sync();
        return "redirect:/sync";
    }
}
