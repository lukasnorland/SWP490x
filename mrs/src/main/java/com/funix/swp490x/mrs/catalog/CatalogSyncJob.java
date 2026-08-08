package com.funix.swp490x.mrs.catalog;

import com.funix.swp490x.mrs.domain.ImportTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Picks up songs uploaded straight to S3, so adding to the catalog needs nothing
 * but the upload.
 *
 * <p>Polling rather than S3 event notifications: the diff is listing-only, which
 * over an unchanged prefix is one request and no reads, so the saving from an
 * event-driven path would not pay for the queue and consumer it needs. If
 * near-real-time is ever wanted, this class is the seam to replace.
 *
 * <p>Registered only when {@code mrs.catalog.sync.enabled} is true, so developer
 * machines and the test suite never poll.
 */
@Component
@ConditionalOnProperty(name = "mrs.catalog.sync.enabled", havingValue = "true")
public class CatalogSyncJob {

    private static final Logger log = LoggerFactory.getLogger(CatalogSyncJob.class);

    private final CatalogImportService importService;

    public CatalogSyncJob(CatalogImportService importService) {
        this.importService = importService;
    }

    /**
     * {@code fixedDelay} measures from the end of the previous run, so a long
     * import cannot have the next tick queued up behind it.
     */
    @Scheduled(fixedDelayString = "${mrs.catalog.sync.interval}", initialDelayString = "PT1M")
    public void sync() {
        ImportSummary summary = importService.sync(ImportTrigger.SCHEDULED, null, false);

        if (summary.alreadyRunning()) {
            return;
        }
        if (summary.isFailed()) {
            // Not rethrown: the next tick recomputes what is still missing from
            // the stored hashes, so a transient outage heals itself.
            log.error("Scheduled catalog sync failed: {}", summary.error());
            return;
        }
        if (summary.isNoChange()) {
            log.debug("Scheduled catalog sync: {} staged object(s), nothing to do",
                    summary.listed());
            return;
        }

        log.info("Scheduled catalog sync: {} added, {} updated, {} skipped",
                summary.added(), summary.updated(), summary.skipped());
    }
}
