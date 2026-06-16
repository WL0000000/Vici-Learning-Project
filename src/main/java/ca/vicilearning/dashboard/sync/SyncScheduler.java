package ca.vicilearning.dashboard.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final SyncService syncService;

    public SyncScheduler(SyncService syncService) {
        this.syncService = syncService;
    }

    // Initial delay = interval so first auto-sync happens 1 hour after startup.
    // Use "Sync Now" on the status page for on-demand syncs.
    @Scheduled(
        fixedDelayString   = "${simplybook.sync-interval-ms:3600000}",
        initialDelayString = "${simplybook.sync-interval-ms:3600000}"
    )
    public void run() {
        log.info("Scheduled sync starting");
        syncService.sync();
    }
}
