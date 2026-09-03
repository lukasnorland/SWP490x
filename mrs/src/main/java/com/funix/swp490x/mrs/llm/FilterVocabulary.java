package com.funix.swp490x.mrs.llm;

import com.funix.swp490x.mrs.catalog.CatalogTaxonomy;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Names the interpreter may emit: MusicBrainz genres, product moods, and tag
 * values already attached to at least one song.
 */
public final class FilterVocabulary {

    private final Map<String, String> genres;
    private final Map<String, String> moods;
    private final Map<String, String> artists;
    private final Map<String, String> tags;
    private final List<String> catalogGenreNames;
    private final List<Phrase> phrases;

    private FilterVocabulary(Map<String, String> genres, Map<String, String> moods,
            Map<String, String> artists, Map<String, String> tags,
            List<String> catalogGenreNames, List<Phrase> phrases) {
        this.genres = Map.copyOf(genres);
        this.moods = Map.copyOf(moods);
        this.artists = Map.copyOf(artists);
        this.tags = Map.copyOf(tags);
        this.catalogGenreNames = List.copyOf(catalogGenreNames);
        this.phrases = List.copyOf(phrases);
    }

    /**
     * Taxonomy lists plus names currently on songs. Synonyms fold through
     * {@link CatalogTaxonomy} when a leftover token is resolved.
     */
    public static FilterVocabulary of(List<Tag> usedOnSongs, CatalogTaxonomy taxonomy) {
        Map<String, String> genres = new LinkedHashMap<>();
        Map<String, String> moods = new LinkedHashMap<>();
        Map<String, String> artists = new LinkedHashMap<>();
        Map<String, String> tags = new LinkedHashMap<>();
        List<String> catalogGenreNames = new ArrayList<>();
        for (String name : taxonomy.allGenres()) {
            put(genres, name);
        }
        for (String name : taxonomy.allMoods()) {
            put(moods, name);
        }
        if (usedOnSongs != null) {
            for (Tag tag : usedOnSongs) {
                if (tag == null || tag.getName() == null || tag.getType() == null) {
                    continue;
                }
                switch (tag.getType()) {
                    case GENRE -> {
                        put(genres, tag.getName());
                        catalogGenreNames.add(tag.getName());
                    }
                    case MOOD -> put(moods, tag.getName());
                    case ARTIST -> put(artists, tag.getName());
                    case TAGS -> put(tags, tag.getName());
                }
            }
        }
        List<Phrase> phrases = new ArrayList<>();
        addPhrases(phrases, TagType.GENRE, genres);
        addPhrases(phrases, TagType.MOOD, moods);
        addPhrases(phrases, TagType.ARTIST, artists);
        addPhrases(phrases, TagType.TAGS, tags);
        phrases.sort((a, b) -> Integer.compare(b.key().length(), a.key().length()));
        return new FilterVocabulary(genres, moods, artists, tags, catalogGenreNames, phrases);
    }

    public Map<String, String> genres() {
        return genres;
    }

    public Map<String, String> moods() {
        return moods;
    }

    public Map<String, String> artists() {
        return artists;
    }

    public Map<String, String> tags() {
        return tags;
    }

    /** Genres attached to at least one song; kept out of the Gemini prompt's giant MB list. */
    public Collection<String> catalogGenreNames() {
        return catalogGenreNames;
    }

    /** Display names, longest first, so "high energy" wins over "energy". */
    public List<Phrase> phrases() {
        return phrases;
    }

    public record Phrase(TagType type, String key, String display) {
    }

    private static void put(Map<String, String> into, String name) {
        String key = normalize(name);
        if (key.isEmpty() || into.containsKey(key)) {
            return;
        }
        into.put(key, name);
    }

    private static void addPhrases(List<Phrase> phrases, TagType type, Map<String, String> byKey) {
        for (Map.Entry<String, String> entry : byKey.entrySet()) {
            if (!entry.getKey().isEmpty()) {
                phrases.add(new Phrase(type, entry.getKey(), entry.getValue()));
            }
        }
    }

    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9&]+", " ").trim()
                .replaceAll("\\s+", " ");
    }
}
