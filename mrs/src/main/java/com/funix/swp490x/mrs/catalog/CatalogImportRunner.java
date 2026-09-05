package com.funix.swp490x.mrs.catalog;

import com.funix.swp490x.mrs.domain.ImportTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Imports the whole prefix at startup when {@code mrs.catalog.import-on-start}
 * is set, which is how the catalog is first populated on a fresh database.
 *
 * <p>Off by default. Leaving it on is harmless but pointless: the ETag diff
 * makes every later boot a single listing with nothing to read.
 */
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
            // Deliberately not fatal: the app is still usable with whatever
            // catalog is already in the database, and P-06b can retry.
            log.error("Startup catalog import failed: {}", summary.error());
            return;
        }

        log.info("Startup catalog import: {} listed, {} read, {} added, {} updated, {} skipped",
                summary.listed(), summary.read(), summary.added(), summary.updated(),
                summary.skipped());
    }
}
