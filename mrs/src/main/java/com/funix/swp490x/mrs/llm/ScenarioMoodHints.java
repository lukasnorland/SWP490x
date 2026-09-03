package com.funix.swp490x.mrs.llm;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Offline mood hints when Gemini is slow or unavailable. Names must exist in
 * {@link FilterVocabulary#moods()}.
 */
final class ScenarioMoodHints {

    private ScenarioMoodHints() {
    }

    static List<String> suggest(String query, FilterVocabulary vocabulary) {
        if (query == null || query.isBlank() || vocabulary == null) {
            return List.of();
        }
        String normalized = FilterVocabulary.normalize(query);
        Set<String> moods = new LinkedHashSet<>();
        if (containsAny(normalized, "festival", "party", "celebration", "carnival")) {
            addKnown(moods, vocabulary, "Celebratory", "Upbeat", "Energetic", "Uplifting");
        }
        if (containsAny(normalized, "summer", "beach", "sun", "vacation")) {
            addKnown(moods, vocabulary, "Upbeat", "Carefree", "Vibrant", "Happy");
        }
        if (containsAny(normalized, "winter", "snow", "holiday")) {
            addKnown(moods, vocabulary, "Cozy", "Nostalgic", "Peaceful", "Mellow");
        }
        if (containsAny(normalized, "relax", "wind down", "calm", "chill", "unwind")) {
            addKnown(moods, vocabulary, "Relaxing", "Calm", "Mellow", "Peaceful");
        }
        if (containsAny(normalized, "workout", "gym", "run", "energy")) {
            addKnown(moods, vocabulary, "Energetic", "High Energy", "Powerful", "Driving");
        }
        if (containsAny(normalized, "romantic", "date night", "love")) {
            addKnown(moods, vocabulary, "Romantic", "Sensual", "Dreamy", "Gentle");
        }
        if (containsAny(normalized, "broken heart", "heartbreak", "heart break", "breakup",
                "break up", "grief", "lonely", "loneliness")) {
            addKnown(moods, vocabulary, "Melancholic", "Sad", "Heartfelt", "Bittersweet");
        }
        if (containsAny(normalized, "focus", "study", "work")) {
            addKnown(moods, vocabulary, "Calm", "Atmospheric", "Smooth", "Melodic");
        }
        return List.copyOf(moods);
    }

    private static boolean containsAny(String normalized, String... needles) {
        for (String needle : needles) {
            String key = FilterVocabulary.normalize(needle);
            if ((" " + normalized + " ").contains(" " + key + " ")) {
                return true;
            }
        }
        return false;
    }

    private static void addKnown(Set<String> moods, FilterVocabulary vocabulary, String... names) {
        Map<String, String> allowed = vocabulary.moods();
        for (String name : names) {
            String canonical = allowed.get(name.toLowerCase(Locale.ROOT));
            if (canonical != null) {
                moods.add(canonical);
            }
        }
    }
}
