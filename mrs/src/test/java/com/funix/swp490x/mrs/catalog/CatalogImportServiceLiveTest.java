package com.funix.swp490x.mrs.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MRS_LIVE_AWS", matches = "1")
class CatalogImportServiceLiveTest {

    @Autowired
    private CatalogImportService importService;

    @Test
    void pendingChangesListsTheRealBucket() {
        CatalogImportService.PendingChanges pending = importService.pendingChanges();
        assertThat(pending.listed()).isGreaterThan(3000);
        assertThat(pending.source()).contains("s3://");
    }
}
