package com.funix.swp490x.mrs.catalog;

import com.funix.swp490x.mrs.domain.TagType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns one staged JSON object into the values an upsert needs, rejecting what
 * the catalog rules do not allow in (SC-05).
 *
 * <p>Rejection is a returned reason rather than an exception: a batch of 3,000
 * objects should import the good ones and report the rest, not stop at the
 * first bad row.
 */
@Component
public class SongJsonMapper {

    /** Longest value {@code song.title} accepts. */
    private static final int MAX_TITLE = 255;
    private static final int MAX_ARTIST = 255;
    private static final int MAX_URL = 500;
    private static final int MAX_ISRC = 20;
    private static final int MAX_TAG_NAME = 100;

    /**
     * Its own mapper rather than the web layer's: this reads third-party files,
     * so its leniency should not shift when the HTTP JSON settings change.
     */
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    /**
     * @param registeredProviders providers allowed into the catalog; an object
     *     naming anything else is rejected whole (SC-05)
     */
    public Result map(String json, List<String> registeredProviders) {
        StagedSong staged;
        try {
            staged = objectMapper.readValue(json, StagedSong.class);
        } catch (Exception e) {
            return Result.rejected("could not be parsed as JSON");
        }

        if (staged == null) {
            return Result.rejected("empty object");
        }
        if (!StringUtils.hasText(staged.title())) {
            return Result.rejected("missing title");
        }
        if (!StringUtils.hasText(staged.externalSourceId())) {
            return Result.rejected("missing externalSourceId");
        }
        if (!StringUtils.hasText(staged.sourceProvider())) {
            return Result.rejected("missing sourceProvider");
        }

        String provider = staged.sourceProvider().trim();
        boolean registered = registeredProviders.stream().anyMatch(p -> p.equalsIgnoreCase(provider));
        if (!registered) {
            return Result.rejected("unregistered provider '" + provider + "'");
        }

        // The column is NOT NULL with a positive check constraint, so a
        // nonsensical duration is dropped rather than failing the whole chunk.
        Integer duration = positiveOrNull(staged.duration());

        SongValues values = new SongValues(
                staged.externalSourceId().trim(),
                provider,
                truncate(staged.title().trim(), MAX_TITLE),
                truncate(trimToNull(staged.artist()), MAX_ARTIST),
                duration,
                positiveOrNull(staged.bpm()),
                staged.isExplicit(),
                truncate(trimToNull(staged.isrc()), MAX_ISRC),
                truncate(trimToNull(staged.audioUrl()), MAX_URL),
                truncate(trimToNull(staged.coverUrl()), MAX_URL),
                tagsOf(staged));

        return Result.mapped(values);
    }

    /**
     * Writes one song in the staged shape, pretty-printed so a human can read
     * the object the same way the vendor dumps are read.
     */
    public String write(StagedSong staged) {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(staged);
    }

    /**
     * Genres, moods and freeform descriptors become tags of their own type; the
     * artist is additionally tagged so search can group by it while the
     * free-text name stays on the song.
     */
    private Set<TagRef> tagsOf(StagedSong staged) {
        Set<TagRef> refs = new LinkedHashSet<>();
        addAll(refs, TagType.GENRE, staged.genres());
        addAll(refs, TagType.MOOD, staged.moods());
        addAll(refs, TagType.TAGS, staged.tags());
        if (StringUtils.hasText(staged.artist())) {
            addAll(refs, TagType.ARTIST, List.of(staged.artist()));
        }
        return refs;
    }

    private void addAll(Set<TagRef> refs, TagType type, List<String> names) {
        if (names == null) {
            return;
        }
        for (String name : names) {
            String cleaned = normaliseTagName(name);
            if (cleaned != null) {
                refs.add(new TagRef(type, cleaned));
            }
        }
    }

    /**
     * Collapses inner whitespace and trims, so "  Laid   Back " and "Laid Back"
     * do not become two rows in a dictionary that is unique on the name.
     */
    private static String normaliseTagName(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            return null;
        }
        return truncate(cleaned, MAX_TAG_NAME);
    }

    private static Integer positiveOrNull(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** A tag to attach, before it is resolved to a row. */
    public record TagRef(TagType type, String name) {

        /** Case-insensitive so "Guitar" and "guitar" resolve to one tag. */
        public String dedupeKey() {
            return type.name() + '\u0000' + name.toLowerCase(Locale.ROOT);
        }
    }

    /** Everything an upsert writes, already validated and trimmed to fit. */
    public record SongValues(
            String externalSourceId,
            String sourceProvider,
            String title,
            String artist,
            Integer duration,
            Integer bpm,
            Boolean explicit,
            String isrc,
            String audioUrl,
            String coverUrl,
            Set<TagRef> tags) {
    }

    /** Either the values to write, or why the object was rejected. */
    public record Result(SongValues values, String rejection) {

        static Result mapped(SongValues values) {
            return new Result(values, null);
        }

        static Result rejected(String reason) {
            return new Result(null, reason);
        }

        public boolean isRejected() {
            return rejection != null;
        }
    }
}
