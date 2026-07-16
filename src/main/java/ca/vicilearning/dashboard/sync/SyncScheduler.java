package ca.vicilearning.dashboard.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Never run the live SimplyBook sync under the `seed` profile: with no upstream credentials the
// sync sees an empty account and soft-deletes every seeded row (the wipe the seeder warns about).
// Seeded demos stay offline; real deployments run without the `seed` profile and sync normally.
@Component
@Profile("!seed")
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
