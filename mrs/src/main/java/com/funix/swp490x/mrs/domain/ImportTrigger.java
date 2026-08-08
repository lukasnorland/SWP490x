package com.funix.swp490x.mrs.domain;

/** What set a catalog import going. */
public enum ImportTrigger {

    /** {@code mrs.catalog.import-on-start}, used for the first bulk load. */
    STARTUP("Startup"),
    /** The periodic poller that picks up objects uploaded straight to S3. */
    SCHEDULED("Scheduled"),
    /** ADMIN pressed Sync on P-06c. */
    MANUAL("Manual");

    private final String displayName;

    ImportTrigger(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
