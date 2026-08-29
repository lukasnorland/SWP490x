package com.funix.swp490x.mrs.catalog;

import com.funix.swp490x.mrs.domain.TagType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

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
    private final CatalogTaxonomy taxonomy = new CatalogTaxonomy();

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
        CatalogTaxonomy.Buckets buckets = taxonomy.classify(
                staged.genres(), staged.moods(), staged.tags());
        StagedSong classified = new StagedSong(
                staged.externalSourceId(),
                staged.sourceProvider(),
                staged.title(),
                staged.artist(),
                staged.duration(),
                staged.bpm(),
                staged.isExplicit(),
                staged.isrc(),
                staged.audioUrl(),
                staged.coverUrl(),
                buckets.genres(),
                buckets.moods(),
                buckets.tags());
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(classified);
    }

    /** Best-effort parse so a delete can collect media URLs without failing the row. */
    public Optional<StagedSong> readStaged(String json) {
        try {
            return Optional.ofNullable(objectMapper.readValue(json, StagedSong.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Overwrites classification on an existing staged object and leaves every
     * other field — licensed identity and unknown provider extras — as they
     * were, so an admin edit does not strip vendor dumps.
     */
    public String patchClassification(String json, boolean explicit, List<String> genres,
            List<String> moods, List<String> tags) {
        JsonNode tree;
        try {
            tree = objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("staged object is not valid JSON", e);
        }
        if (tree == null || !tree.isObject()) {
            throw new IllegalArgumentException("staged object is not a JSON object");
        }
        ObjectNode object = (ObjectNode) tree;
        CatalogTaxonomy.Buckets buckets = taxonomy.classify(genres, moods, tags);
        object.put("isExplicit", explicit);
        object.set("genres", objectMapper.valueToTree(buckets.genres()));
        object.set("moods", objectMapper.valueToTree(buckets.moods()));
        object.set("tags", objectMapper.valueToTree(buckets.tags()));
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
    }

    /**
     * Rebuilds {@code genres}/{@code moods}/{@code tags} when names sit in the
     * wrong array or the casing drifted. Empty when the object is already in
     * step, so a bulk rewrite can skip the put.
     */
    public Optional<String> reclassifyJson(String json) {
        Optional<StagedSong> staged = readStaged(json);
        if (staged.isEmpty()) {
            return Optional.empty();
        }
        StagedSong song = staged.get();
        List<String> genres = song.genres() == null ? List.of() : song.genres();
        List<String> moods = song.moods() == null ? List.of() : song.moods();
        List<String> tags = song.tags() == null ? List.of() : song.tags();
        CatalogTaxonomy.Buckets buckets = taxonomy.classify(genres, moods, tags);
        if (buckets.genres().equals(genres)
                && buckets.moods().equals(moods)
                && buckets.tags().equals(tags)) {
            return Optional.empty();
        }
        return Optional.of(patchClassification(json, Boolean.TRUE.equals(song.isExplicit()),
                genres, moods, tags));
    }

    /** Title Case and drop blanks, without moving a name to another vocabulary. */
    public List<String> cleanNames(List<String> names) {
        if (names == null) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> cleaned = new ArrayList<>();
        for (String name : names) {
            String normalised = normaliseTagName(name);
            if (normalised != null && seen.add(normalised.toLowerCase(Locale.ROOT))) {
                cleaned.add(normalised);
            }
        }
        return cleaned;
    }

    /**
     * Genres, moods and freeform descriptors become tags of their own type; the
     * artist is additionally tagged so search can group by it while the
     * free-text name stays on the song. Known subgenres or moods that arrived
     * under {@code tags} are attached as GENRE / MOOD so the filters stay
     * unmixed even before the staged JSON is rewritten.
     */
    public Set<TagRef> tagRefs(List<String> genres, List<String> moods, List<String> tags,
            String artist) {
        CatalogTaxonomy.Buckets buckets = taxonomy.classify(genres, moods, tags);
        Set<TagRef> refs = new LinkedHashSet<>();
        addAll(refs, TagType.GENRE, buckets.genres());
        addAll(refs, TagType.MOOD, buckets.moods());
        addAll(refs, TagType.TAGS, buckets.tags());
        if (StringUtils.hasText(artist)) {
            addAll(refs, TagType.ARTIST, List.of(artist.trim().replaceAll("\\s+", " ")));
        }
        return refs;
    }

    private Set<TagRef> tagsOf(StagedSong staged) {
        return tagRefs(staged.genres(), staged.moods(), staged.tags(), staged.artist());
    }

    private void addAll(Set<TagRef> refs, TagType type, List<String> names) {
        if (names == null) {
            return;
        }
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                refs.add(new TagRef(type, truncate(name, MAX_TAG_NAME)));
            }
        }
    }

    /**
     * Collapses inner whitespace, folds synonyms, and Title Cases, so
     * "high energy" and "High Energy" do not become two dictionary rows.
     */
    private String normaliseTagName(String raw) {
        String canonical = taxonomy.canonicalName(raw);
        if (canonical == null) {
            return null;
        }
        return truncate(canonical, MAX_TAG_NAME);
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
