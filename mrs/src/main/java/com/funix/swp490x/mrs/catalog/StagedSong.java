package com.funix.swp490x.mrs.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The staged JSON shape for one catalog song.
 *
 * <p>Unknown fields are ignored on purpose: the provider dumps carry more than
 * the catalog keeps (stems, slugs, per-format URLs), and a new field appearing
 * upstream must not fail an import.
 *
 * <p>{@code isExplicit} is named explicitly so serialising this record produces
 * the same key the 5,000+ existing objects use, rather than Jackson's bean
 * default of {@code explicit}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StagedSong(
        String externalSourceId,
        String sourceProvider,
        String title,
        String artist,
        Integer duration,
        Integer bpm,
        @JsonProperty("isExplicit") Boolean isExplicit,
        String isrc,
        String audioUrl,
        String coverUrl,
        List<String> genres,
        List<String> moods,
        List<String> tags) {
}
