package com.funix.swp490x.mrs.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDirectoryCatalogObjectStoreTest {

    @TempDir
    private Path root;

    private LocalDirectoryCatalogObjectStore store;

    @BeforeEach
    void setUp() {
        store = new LocalDirectoryCatalogObjectStore(root);
    }

    @Test
    void putJsonWritesAFileThatListAndReadCanSee() throws Exception {
        String etag = store.putJson("song-a.json", "{\"title\":\"A\"}");

        assertThat(Files.readString(root.resolve("song-a.json"))).contains("\"title\":\"A\"");
        assertThat(store.list()).singleElement().satisfies(object -> {
            assertThat(object.key()).isEqualTo("song-a.json");
            assertThat(object.etag()).isEqualTo(etag);
        });
        assertThat(store.readJson("song-a.json")).contains("\"title\":\"A\"");
    }

    @Test
    void findJsonIsEmptyWhenTheObjectIsMissing() {
        assertThat(store.findJson("never-there.json")).isEmpty();
    }

    @Test
    void stagingKeyIsTheExternalIdWithJsonSuffix() {
        assertThat(store.stagingKey("abc-123")).isEqualTo("abc-123.json");
    }

    @Test
    void putJsonRejectsAKeyThatEscapesTheRoot() {
        assertThatThrownBy(() -> store.putJson("../escape.json", "{}"))
                .isInstanceOf(CatalogStoreException.class)
                .hasMessageContaining("escapes");
    }

    @Test
    void putJsonOverwritesAnExistingObject() throws Exception {
        store.putJson("song-a.json", "{\"v\":1}");
        store.putJson("song-a.json", "{\"v\":2}");

        assertThat(store.readJson("song-a.json")).contains("\"v\":2");
    }

    @Test
    void deleteJsonRemovesAStagedObject() throws Exception {
        store.putJson("song-a.json", "{\"title\":\"A\"}");

        store.deleteJson("song-a.json");

        assertThat(store.list()).isEmpty();
        assertThatThrownBy(() -> store.readJson("song-a.json"))
                .isInstanceOf(CatalogStoreException.class);
    }

    @Test
    void deleteJsonOfAMissingObjectIsNotAnError() {
        store.deleteJson("never-there.json");

        assertThat(store.list()).isEmpty();
    }

    @Test
    void deleteJsonRejectsAKeyThatEscapesTheRoot() {
        assertThatThrownBy(() -> store.deleteJson("../escape.json"))
                .isInstanceOf(CatalogStoreException.class)
                .hasMessageContaining("escapes");
    }

    @Test
    void putBinaryWritesNestedMediaWithoutListingItAsASong() throws Exception {
        store.putBinary("song-data/audio/ncs/id-1.mp3", "audio/mpeg",
                new java.io.ByteArrayInputStream(new byte[] {1, 2, 3}), 3);

        assertThat(Files.readAllBytes(root.resolve("song-data/audio/ncs/id-1.mp3")))
                .containsExactly(1, 2, 3);
        assertThat(store.list()).isEmpty();
        assertThat(store.mediaKey(MediaKind.AUDIO, "ncs", "id-1", "mp3"))
                .isEqualTo("song-data/audio/ncs/id-1.mp3");
    }

    @Test
    void deleteBinaryRemovesMediaWithoutListingItAsASong() throws Exception {
        store.putBinary("song-data/audio/ncs/id-1.mp3", "audio/mpeg",
                new java.io.ByteArrayInputStream(new byte[] {1, 2, 3}), 3);

        store.deleteBinary("song-data/audio/ncs/id-1.mp3");

        assertThat(Files.exists(root.resolve("song-data/audio/ncs/id-1.mp3"))).isFalse();
        assertThat(store.list()).isEmpty();
    }

    @Test
    void deleteBinaryOfAMissingObjectIsNotAnError() {
        store.deleteBinary("song-data/audio/ncs/never.mp3");
    }

    @Test
    void listOfAMissingDirectoryIsEmptyRatherThanAnError() {
        LocalDirectoryCatalogObjectStore missing =
                new LocalDirectoryCatalogObjectStore(root.resolve("does-not-exist"));

        assertThat(missing.list()).isEmpty();
    }
}
