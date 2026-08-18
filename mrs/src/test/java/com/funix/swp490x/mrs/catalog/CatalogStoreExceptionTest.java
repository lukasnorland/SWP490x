package com.funix.swp490x.mrs.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CatalogStoreExceptionTest {

    @Test
    void ofDoesNotReWrapAnExistingCatalogStoreException() {
        CatalogStoreException original = new CatalogStoreException("already wrapped");
        assertThat(CatalogStoreException.of("list", original)).isSameAs(original);
    }

    @Test
    void deepestMessagePrefersTheAwsCliLineOverTheSdkWrapper() {
        IllegalStateException expired = new IllegalStateException(
                "Failed to refresh process-based credentials.",
                new IllegalStateException("Command returned non-zero exit value (255) "
                        + "with error message: \naws: [ERROR]: Your session has expired. "
                        + "Please reauthenticate using 'aws login'."));

        assertThat(CatalogStoreException.deepestMessage(expired))
                .contains("session has expired")
                .doesNotContain("Failed to refresh");
    }
}
