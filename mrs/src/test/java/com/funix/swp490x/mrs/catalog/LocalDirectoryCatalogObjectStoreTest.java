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
        store.putJson("song-a.json", "{\"title\":\"A\"}");

        assertThat(Files.readString(root.resolve("song-a.json"))).contains("\"title\":\"A\"");
        assertThat(store.list()).singleElement().satisfies(object -> {
            assertThat(object.key()).isEqualTo("song-a.json");
            assertThat(object.etag()).isNotBlank();
        });
        assertThat(store.readJson("song-a.json")).contains("\"title\":\"A\"");
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
}
