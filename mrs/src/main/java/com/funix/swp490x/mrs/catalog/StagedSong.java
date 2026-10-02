package com.funix.swp490x.mrs.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Staged song JSON; unknown provider fields are ignored during import.
 * Serializes {@code isExplicit} with the key expected by staged objects.
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
