package com.funix.swp490x.mrs.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.funix.swp490x.mrs.aws.AwsCredentialsFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Optional live check against the real bucket. Run with
 * {@code MRS_LIVE_AWS=1} when diagnosing credential or permission issues.
 */
@EnabledIfEnvironmentVariable(named = "MRS_LIVE_AWS", matches = "1")
class S3CatalogObjectStoreLiveTest {

    @Test
    void listsTheStagedPrefix() {
        try (S3Client s3 = S3Client.builder()
                .region(Region.AP_SOUTHEAST_1)
                .credentialsProvider(AwsCredentialsFactory.forProfile("mrs-admin", "test"))
                .build()) {
            CatalogObjectStore store = new S3CatalogObjectStore(s3,
                    "mrs-133857166188-assets", "song-data/");
            List<CatalogObject> objects = store.list();
            assertThat(objects).isNotEmpty();
        }
    }
}
