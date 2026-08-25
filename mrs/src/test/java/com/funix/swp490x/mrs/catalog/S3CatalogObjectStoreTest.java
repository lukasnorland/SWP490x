package com.funix.swp490x.mrs.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Credential refresh fails with {@link IllegalStateException}, not an SDK
 * exception. The store must wrap that so P-06c can render instead of 500.
 */
class S3CatalogObjectStoreTest {

    private S3Client s3;
    private S3CatalogObjectStore store;

    @BeforeEach
    void setUp() {
        s3 = mock(S3Client.class);
        store = new S3CatalogObjectStore(s3, "mrs-assets", "song-data/");
    }

    @Test
    void stagedSongKeysAreOnlyJsonDirectlyUnderThePrefix() {
        assertThat(S3CatalogObjectStore.isStagedSongKey("song-data/", "song-data/3.json"))
                .isTrue();
        assertThat(S3CatalogObjectStore.isStagedSongKey("song-data/",
                "song-data/one-off/3.json")).isFalse();
        assertThat(S3CatalogObjectStore.isStagedSongKey("song-data/",
                "song-data/ncs/GB2LD0901581.json")).isFalse();
        assertThat(S3CatalogObjectStore.isStagedSongKey("song-data/",
                "song-data/audio/ncs/id.json")).isFalse();
    }

    @Test
    void listWrapsAnExpiredAwsLoginAsCatalogStoreException() {
        given(s3.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
                .willThrow(expiredLogin());

        assertThatThrownBy(store::list)
                .isInstanceOf(CatalogStoreException.class)
                .hasMessageContaining("session has expired")
                .hasMessageContaining("s3://mrs-assets/song-data/");
    }

    @Test
    void putBinaryWrapsAnExpiredAwsLoginAsCatalogStoreException() {
        given(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willThrow(expiredLogin());

        assertThatThrownBy(() -> store.putBinary("song-data/audio/ncs/id.mp3",
                "audio/mpeg", new ByteArrayInputStream(new byte[] {1}), 1))
                .isInstanceOf(CatalogStoreException.class)
                .hasMessageContaining("session has expired");
    }

    private static IllegalStateException expiredLogin() {
        return new IllegalStateException("Failed to refresh process-based credentials.",
                new IllegalStateException("Command returned non-zero exit value (255) "
                        + "with error message: \naws: [ERROR]: Your session has expired. "
                        + "Please reauthenticate using 'aws login'."));
    }
}
