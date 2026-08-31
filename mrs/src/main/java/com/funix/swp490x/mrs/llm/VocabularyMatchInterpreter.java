package com.funix.swp490x.mrs.llm;

import com.funix.swp490x.mrs.catalog.CatalogTaxonomy;
import com.funix.swp490x.mrs.domain.TagType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * BR-07 keyword fallback: map prompt tokens onto genre / mood / artist / tag
 * names with no network call. Gemini will sit in front of this later.
 */
public class VocabularyMatchInterpreter implements LlmInterpreter {

    private final CatalogTaxonomy taxonomy;

    public VocabularyMatchInterpreter(CatalogTaxonomy taxonomy) {
        this.taxonomy = taxonomy == null ? new CatalogTaxonomy() : taxonomy;
    }

    @Override
    public Optional<InterpretedFilters> interpret(String query, FilterVocabulary vocabulary) {
        if (!StringUtils.hasText(query) || vocabulary == null) {
            return Optional.empty();
        }
        String padded = " " + FilterVocabulary.normalize(query) + " ";
        if (padded.isBlank()) {
            return Optional.empty();
        }

        Set<String> genres = new LinkedHashSet<>();
        Set<String> moods = new LinkedHashSet<>();
        Set<String> artists = new LinkedHashSet<>();
        Set<String> tags = new LinkedHashSet<>();

        for (FilterVocabulary.Phrase phrase : vocabulary.phrases()) {
            String needle = " " + phrase.key() + " ";
            if (!padded.contains(needle)) {
                continue;
            }
            add(phrase.type(), phrase.display(), genres, moods, artists, tags);
            padded = padded.replace(needle, " ");
        }

        String leftover = FilterVocabulary.normalize(padded);
        if (!leftover.isEmpty()) {
            resolveLeftovers(leftover, vocabulary, genres, moods, artists, tags);
        }

        InterpretedFilters filters = new InterpretedFilters(
                List.copyOf(genres), List.copyOf(moods), List.copyOf(artists),
                List.copyOf(tags), null);
        return filters.isEmpty() ? Optional.empty() : Optional.of(filters);
    }

    /**
     * Unmatched words (and adjacent pairs) through the taxonomy so "edm" and
     * "high-energy" still land on allowlisted display names.
     */
    private void resolveLeftovers(String leftover, FilterVocabulary vocabulary,
            Set<String> genres, Set<String> moods, Set<String> artists, Set<String> tags) {
        List<String> tokens = List.of(leftover.split(" "));
        List<String> windows = new ArrayList<>();
        for (int i = 0; i < tokens.size() - 1; i++) {
            windows.add(tokens.get(i) + " " + tokens.get(i + 1));
        }
        windows.addAll(tokens);
        for (String window : windows) {
            String genre = taxonomy.resolveGenre(window);
            if (genre != null) {
                add(TagType.GENRE, genre, genres, moods, artists, tags);
                continue;
            }
            String mood = taxonomy.resolveMood(window);
            if (mood != null) {
                add(TagType.MOOD, mood, genres, moods, artists, tags);
                continue;
            }
            String artist = vocabulary.artists().get(FilterVocabulary.normalize(window));
            if (artist != null) {
                add(TagType.ARTIST, artist, genres, moods, artists, tags);
                continue;
            }
            String tag = vocabulary.tags().get(FilterVocabulary.normalize(window));
            if (tag != null) {
                add(TagType.TAGS, tag, genres, moods, artists, tags);
            }
        }
    }

    private static void add(TagType type, String display, Set<String> genres, Set<String> moods,
            Set<String> artists, Set<String> tags) {
        if (display == null || display.isBlank()) {
            return;
        }
        switch (type) {
            case GENRE -> genres.add(display);
            case MOOD -> moods.add(display);
            case ARTIST -> artists.add(display);
            case TAGS -> tags.add(display);
        }
    }
}
