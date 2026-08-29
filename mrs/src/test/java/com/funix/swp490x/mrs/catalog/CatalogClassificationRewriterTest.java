package com.funix.swp490x.mrs.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogClassificationRewriterTest {

    @TempDir
    private Path dir;

    @Test
    void dryRunDoesNotWriteAndWriteMovesUnknownGenresToTags() throws Exception {
        Path file = dir.resolve("song.json");
        Files.writeString(file, """
                {"externalSourceId":"x1","sourceProvider":"NCS","title":"T",
                 "isExplicit":false,"genres":["Electronic","Cinematic"],"moods":["Energetic"],
                 "tags":["female vocals"]}
                """, StandardCharsets.UTF_8);

        CatalogClassificationRewriter.run(new LocalDirectoryCatalogObjectStore(dir), false);
        assertThat(Files.readString(file)).contains("\"Cinematic\"");

        CatalogClassificationRewriter.run(new LocalDirectoryCatalogObjectStore(dir), true);
        String written = Files.readString(file);
        assertThat(written).contains("\"Electronic\"");
        assertThat(written).contains("\"Energetic\"");
        assertThat(written).contains("\"Cinematic\"");
        assertThat(written).contains("\"Female Vocals\"");
        assertThat(new SongJsonMapper().reclassifyJson(written)).isEmpty();
    }
}
