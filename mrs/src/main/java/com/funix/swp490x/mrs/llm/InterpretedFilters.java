package com.funix.swp490x.mrs.llm;

import java.util.List;

/**
 * Structured interpretation of a playlist-need prompt (Gemini response schema).
 *
 * <p>Names, not ids: the mapper resolves them against tags already on songs and
 * drops anything unknown. Empty lists mean unconstrained.
 */
public record InterpretedFilters(
        List<String> genres,
        List<String> moods,
        List<String> artists,
        List<String> tags,
        Double confidence) {

    public InterpretedFilters {
        genres = copy(genres);
        moods = copy(moods);
        artists = copy(artists);
        tags = copy(tags);
    }

    public static InterpretedFilters empty() {
        return new InterpretedFilters(List.of(), List.of(), List.of(), List.of(), null);
    }

    public boolean isEmpty() {
        return genres.isEmpty() && moods.isEmpty() && artists.isEmpty() && tags.isEmpty();
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
