package com.funix.swp490x.mrs.catalog;

import java.util.List;

/**
 * An ADMIN typed a genre or mood that is not on the allowlist. Bulk import
 * still demotes unknowns to Tags; the catalog form refuses them so the
 * vocabularies cannot drift again.
 */
public class InvalidClassificationException extends RuntimeException {

    private final List<String> unknownGenres;
    private final List<String> unknownMoods;

    public InvalidClassificationException(List<String> unknownGenres, List<String> unknownMoods) {
        super(messageOf(unknownGenres, unknownMoods));
        this.unknownGenres = List.copyOf(unknownGenres);
        this.unknownMoods = List.copyOf(unknownMoods);
    }

    public List<String> unknownGenres() {
        return unknownGenres;
    }

    public List<String> unknownMoods() {
        return unknownMoods;
    }

    private static String messageOf(List<String> genres, List<String> moods) {
        List<String> parts = new java.util.ArrayList<>();
        if (genres != null && !genres.isEmpty()) {
            parts.add("unknown genre(s): " + String.join(", ", genres));
        }
        if (moods != null && !moods.isEmpty()) {
            parts.add("unknown mood(s): " + String.join(", ", moods));
        }
        if (parts.isEmpty()) {
            return "Unknown genre or mood.";
        }
        return "Pick a listed genre or mood — " + String.join("; ", parts) + ".";
    }
}
