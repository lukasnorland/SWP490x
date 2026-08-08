package com.funix.swp490x.mrs.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The staged JSON shape written by the scripts under {@code scripts/}.
 *
 * <p>Unknown fields are ignored on purpose: the provider dumps carry more than
 * the catalog keeps (stems, slugs, per-format URLs), and a new field appearing
 * upstream must not fail an import.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StagedSong(
        String externalSourceId,
        String sourceProvider,
        String title,
        String artist,
        Integer duration,
        Integer bpm,
        String energyLevel,
        Boolean hasVocals,
        Boolean isExplicit,
        String isrc,
        String audioUrl,
        String coverUrl,
        List<String> genres,
        List<String> moods,
        List<String> tags) {
}
