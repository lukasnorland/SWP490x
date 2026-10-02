package com.funix.swp490x.mrs.catalog;

import com.funix.swp490x.mrs.domain.ImportTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Runs startup sync when {@code mrs.catalog.import-on-start} is enabled; off by default. */
@Component
@ConditionalOnProperty(name = "mrs.catalog.import-on-start", havingValue = "true")
public class CatalogImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogImportRunner.class);

    private final CatalogImportService importService;

    public CatalogImportRunner(CatalogImportService importService) {
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("mrs.catalog.import-on-start is set; importing the catalog now");
        ImportSummary summary = importService.sync(ImportTrigger.STARTUP, null, false);

        if (summary.isFailed()) {
            // Keep the existing catalog available; ADMIN can retry sync from P-06b.
            log.error("Startup catalog import failed: {}", summary.error());
            return;
        }

        log.info("Startup catalog import: {} listed, {} read, {} added, {} updated, {} skipped",
                summary.listed(), summary.read(), summary.added(), summary.updated(),
                summary.skipped());
    }
}
