package com.funix.swp490x.mrs.catalog;

import com.funix.swp490x.mrs.domain.TagType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Closed vocabularies so Genre, Mood and Tags do not mix.
 *
 * <p>Genres are the cached MusicBrainz list. Moods are the product-owned
 * adjectives. Everything else is a freeform Tag, Title Cased. Admin save
 * refuses unknown genres and moods; bulk remap demotes them to Tags.
 */
public final class CatalogTaxonomy {

    private static final Pattern ERA = Pattern.compile("^(?:\\d{2}|\\d{4})s$", Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> GENRES = new HashMap<>();
    private static final Map<String, String> MOODS = new HashMap<>();
    private static final Map<String, String> SYNONYMS = new HashMap<>();
    private static final List<String> GENRE_DISPLAY;
    private static final List<String> MOOD_DISPLAY;

    static {
        putMood("Aggressive", "agressive");
        putMood("Angry");
        putMood("Anthemic", "anthematic", "anthemical", "anthem");
        putMood("Anxious", "anxiety");
        putMood("Atmospheric");
        putMood("Bittersweet");
        putMood("Busy & Frantic", "busy and frantic");
        putMood("Calm");
        putMood("Carefree");
        putMood("Catchy");
        putMood("Cathartic");
        putMood("Celebratory");
        putMood("Chaotic", "chaos");
        putMood("Chasing");
        putMood("Cheerful");
        putMood("Chill");
        putMood("Confident");
        putMood("Cozy", "cosy");
        putMood("Cute");
        putMood("Dark");
        putMood("Danceable");
        putMood("Determined");
        putMood("Disorienting");
        putMood("Dramatic");
        putMood("Dreamy", "dreamlike");
        putMood("Driving");
        putMood("Dynamic");
        putMood("Eccentric");
        putMood("Elegant");
        putMood("Emotional");
        putMood("Empowering", "empowered", "empowerment");
        putMood("Energetic");
        putMood("Epic");
        putMood("Euphoric", "euphoria");
        putMood("Explosive");
        putMood("Fear");
        putMood("Floating");
        putMood("Frantic");
        putMood("Funny");
        putMood("Futuristic");
        putMood("Gentle");
        putMood("Glamorous");
        putMood("Gritty");
        putMood("Groovy");
        putMood("Happy");
        putMood("Haunting");
        putMood("Heartfelt");
        putMood("Heavy");
        putMood("Heavy & Ponderous", "heavy and ponderous");
        putMood("High Energy", "high-energy", "highenergy", "high-octane", "high octane");
        putMood("Hopeful");
        putMood("Hypnotic");
        putMood("Intense");
        putMood("Introspective", "introspection");
        putMood("Joyful", "joyous");
        putMood("Laid Back", "laid-back", "laidback");
        putMood("Melancholic", "melancholy");
        putMood("Mellow");
        putMood("Melodic");
        putMood("Mysterious");
        putMood("Mystical");
        putMood("Nostalgic", "nostalgia");
        putMood("Passionate");
        putMood("Peaceful");
        putMood("Playful");
        putMood("Powerful");
        putMood("Quirky");
        putMood("Reflective", "reflection");
        putMood("Relaxing", "relaxed");
        putMood("Restless");
        putMood("Romantic");
        putMood("Running");
        putMood("Sad");
        putMood("Sentimental");
        putMood("Sexy");
        putMood("Smooth");
        putMood("Sneaking");
        putMood("Soaring");
        putMood("Soulful");
        putMood("Suspense", "suspenseful");
        putMood("Tense", "tension");
        putMood("Triumphant");
        putMood("Upbeat");
        putMood("Uplifting");
        putMood("Urgent", "urgency");
        putMood("Vibrant");
        putMood("Weird");

        alias("r&b", "R&B");
        alias("rnb", "R&B");
        alias("edm", "EDM");
        alias("ncs", "NCS");
        alias("uk", "UK");
        alias("usa", "USA");
        alias("k-pop", "K-Pop");
        alias("kpop", "K-Pop");
        alias("j-pop", "J-Pop");
        alias("jpop", "J-Pop");
        alias("j-rock", "J-Rock");
        alias("j-core", "J-Core");
        alias("v-pop", "V-Pop");
        alias("lo-fi", "Lo-Fi");
        alias("lofi", "Lo-Fi");
        alias("hip-hop", "Hip Hop");
        alias("hip hop", "Hip Hop");
        alias("hiphop", "Hip Hop");
        alias("agressive", "Aggressive");
        alias("high-energy", "High Energy");
        alias("high energy", "High Energy");
        alias("feelgood", "Feel Good");
        alias("feel good", "Feel Good");
        alias("feel-good", "Feel Good");
        alias("drum and bass", "Drum And Bass");
        alias("children's music", "Children's Music");
        alias("childrens music", "Children's Music");
        alias("nu-disco", "Nu Disco");
        alias("nu disco", "Nu Disco");
        alias("nu-metal", "Nu Metal");

        for (String raw : MusicBrainzGenreCatalog.get().rawNames()) {
            indexGenre(raw);
        }

        aliasGenre("alternative", "alternative rock");
        aliasGenre("alt", "alternative rock");
        aliasGenre("alt-rock", "alternative rock");
        aliasGenre("alt rock", "alternative rock");
        aliasGenre("rock alternative", "alternative rock");
        aliasGenre("alt-pop", "alternative pop");
        aliasGenre("alt pop", "alternative pop");
        aliasGenre("indie", "indie rock");
        aliasGenre("rap", "hip hop");
        aliasGenre("hiphop", "hip hop");
        aliasGenre("hip-hop", "hip hop");
        aliasGenre("hip-hop/rap", "hip hop");
        aliasGenre("hiphop/rap", "hip hop");
        aliasGenre("hip hop/rap", "hip hop");
        aliasGenre("brazilian funk", "funk carioca");
        aliasGenre("baile funk", "funk carioca");
        aliasGenre("favela funk", "funk carioca");
        aliasGenre("brega funk", "funk carioca");
        aliasGenre("hardcore", "hardcore techno");
        aliasGenre("soft house", "house");
        aliasGenre("nu-disco", "nu disco");
        aliasGenre("nu-metal", "nu metal");
        aliasGenre("nu metal", "nu metal");
        aliasGenre("rnb", "r&b");
        aliasGenre("r and b", "r&b");
        aliasGenre("r & b", "r&b");
        aliasGenre("lofi", "lo-fi");
        aliasGenre("lo fi", "lo-fi");
        aliasGenre("dnb", "drum and bass");
        aliasGenre("d&b", "drum and bass");
        aliasGenre("drum & bass", "drum and bass");
        aliasGenre("kpop", "k-pop");
        aliasGenre("k pop", "k-pop");
        aliasGenre("korean pop", "k-pop");
        aliasGenre("jpop", "j-pop");
        aliasGenre("j pop", "j-pop");
        aliasGenre("jrock", "j-rock");
        aliasGenre("j rock", "j-rock");
        aliasGenre("jcore", "j-core");
        aliasGenre("big room", "big room house");
        aliasGenre("ukg", "uk garage");
        aliasGenre("electronic dance music", "edm");
        aliasGenre("electronic dance music (edm)", "edm");
        aliasGenre("hyper pop", "hyperpop");
        aliasGenre("electro pop", "electropop");
        aliasGenre("electro-pop", "electropop");
        aliasGenre("dance pop", "dance-pop");
        aliasGenre("synth pop", "synth-pop");
        aliasGenre("modern r&b", "contemporary r&b");
        aliasGenre("pop-r&b", "contemporary r&b");
        aliasGenre("pop r&b", "contemporary r&b");
        aliasGenre("afro beats", "afrobeats");
        aliasGenre("afro-beats", "afrobeats");
        aliasGenre("bossa", "bossa nova");
        aliasGenre("brazilian bossa nova", "bossa nova");
        aliasGenre("liquid drum and bass", "drum and bass");
        aliasGenre("west coast hip-hop", "west coast hip hop");
        aliasGenre("west coast rap", "west coast hip hop");
        aliasGenre("lofi hip hop", "lo-fi hip hop");
        aliasGenre("lo-fi hip-hop", "lo-fi hip hop");

        GENRE_DISPLAY = GENRES.values().stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        MOOD_DISPLAY = MOODS.values().stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public record Buckets(List<String> genres, List<String> moods, List<String> tags) {
    }

    /**
     * Whitespace collapse, synonym fold, then Title Case. {@code null} when
     * the raw value is blank.
     */
    public String canonicalName(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            return null;
        }
        String key = cleaned.toLowerCase(Locale.ROOT);
        String known = firstNonNull(SYNONYMS.get(key), GENRES.get(key), foldGenre(key),
                MOODS.get(key));
        if (known != null) {
            return known;
        }
        return titleCase(cleaned);
    }

    /**
     * Sends every name to one vocabulary. A name listed under the wrong JSON
     * field still lands in Genre or Mood when the taxonomy knows it. Unknown
     * names become Tags so a bulk remap of vendor dumps does not fail.
     */
    public Buckets classify(List<String> genres, List<String> moods, List<String> tags) {
        LinkedHashSet<String> outGenres = new LinkedHashSet<>();
        LinkedHashSet<String> outMoods = new LinkedHashSet<>();
        LinkedHashSet<String> outTags = new LinkedHashSet<>();
        Set<String> seen = new HashSet<>();
        consume(genres, outGenres, outMoods, outTags, seen);
        consume(moods, outGenres, outMoods, outTags, seen);
        consume(tags, outGenres, outMoods, outTags, seen);
        return new Buckets(List.copyOf(outGenres), List.copyOf(outMoods), List.copyOf(outTags));
    }

    /**
     * ADMIN save/upload: every non-blank genre and mood must be allowlisted.
     * Tags stay freeform.
     */
    public void requireAllowlisted(List<String> genres, List<String> moods) {
        List<String> unknownGenres = unknownOf(genres, true);
        List<String> unknownMoods = unknownOf(moods, false);
        if (!unknownGenres.isEmpty() || !unknownMoods.isEmpty()) {
            throw new InvalidClassificationException(unknownGenres, unknownMoods);
        }
    }

    public List<String> allGenres() {
        return GENRE_DISPLAY;
    }

    public List<String> allMoods() {
        return MOOD_DISPLAY;
    }

    /**
     * Prefix matches first, then other substrings, capped at 50.
     */
    public List<String> suggest(TagType type, String query, int limit) {
        List<String> source = type == TagType.GENRE ? GENRE_DISPLAY
                : type == TagType.MOOD ? MOOD_DISPLAY : List.of();
        int cap = Math.min(Math.max(limit, 1), 50);
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return source.stream().limit(cap).toList();
        }
        List<String> prefix = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (String name : source) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith(q)) {
                prefix.add(name);
            } else if (lower.contains(q)) {
                rest.add(name);
            }
            if (prefix.size() >= cap) {
                break;
            }
        }
        List<String> out = new ArrayList<>(prefix);
        for (String name : rest) {
            if (out.size() >= cap) {
                break;
            }
            out.add(name);
        }
        return List.copyOf(out);
    }

    /**
     * Where a lone name belongs. Unknown styles and feelings become Tags.
     */
    public TagType typeOf(String displayName) {
        return route(displayName);
    }

    public String resolveGenre(String raw) {
        String cleaned = raw == null ? null : raw.trim().replaceAll("\\s+", " ");
        if (cleaned == null || cleaned.isEmpty()) {
            return null;
        }
        return firstNonNull(GENRES.get(cleaned.toLowerCase(Locale.ROOT)),
                foldGenre(cleaned.toLowerCase(Locale.ROOT)));
    }

    public String resolveMood(String raw) {
        String cleaned = raw == null ? null : raw.trim().replaceAll("\\s+", " ");
        if (cleaned == null || cleaned.isEmpty()) {
            return null;
        }
        return MOODS.get(cleaned.toLowerCase(Locale.ROOT));
    }

    private void consume(List<String> names, Set<String> genres, Set<String> moods,
            Set<String> tags, Set<String> seen) {
        if (names == null) {
            return;
        }
        for (String raw : names) {
            String display = canonicalName(raw);
            if (display == null) {
                continue;
            }
            String key = display.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                continue;
            }
            switch (route(display)) {
                case GENRE -> genres.add(display);
                case MOOD -> moods.add(display);
                default -> tags.add(display);
            }
        }
    }

    private TagType route(String display) {
        if (ERA.matcher(display).matches()) {
            return TagType.TAGS;
        }
        if (resolveGenre(display) != null) {
            return TagType.GENRE;
        }
        if (resolveMood(display) != null) {
            return TagType.MOOD;
        }
        return TagType.TAGS;
    }

    private List<String> unknownOf(List<String> names, boolean genres) {
        List<String> unknown = new ArrayList<>();
        if (names == null) {
            return unknown;
        }
        Set<String> seen = new HashSet<>();
        for (String raw : names) {
            String cleaned = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
            if (cleaned.isEmpty()) {
                continue;
            }
            String key = cleaned.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                continue;
            }
            boolean known = genres ? resolveGenre(cleaned) != null : resolveMood(cleaned) != null;
            if (!known) {
                unknown.add(cleaned);
            }
        }
        return unknown;
    }

    static String titleCase(String cleaned) {
        String[] words = cleaned.split(" ");
        List<String> parts = new ArrayList<>(words.length);
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if ("&".equals(word)) {
                parts.add("&");
                continue;
            }
            String folded = SYNONYMS.get(word.toLowerCase(Locale.ROOT));
            if (folded != null && !folded.contains(" ")) {
                parts.add(folded);
                continue;
            }
            if (word.indexOf('-') >= 0) {
                String[] bits = word.split("-", -1);
                List<String> titled = new ArrayList<>(bits.length);
                for (String bit : bits) {
                    titled.add(titleToken(bit));
                }
                parts.add(String.join("-", titled));
                continue;
            }
            parts.add(titleToken(word));
        }
        return collapseAdjacentDuplicates(String.join(" ", parts));
    }

    private static String titleToken(String word) {
        if (word.isEmpty()) {
            return word;
        }
        String folded = SYNONYMS.get(word.toLowerCase(Locale.ROOT));
        if (folded != null && !folded.contains(" ")) {
            return folded;
        }
        int first = word.offsetByCodePoints(0, 1);
        return word.substring(0, first).toUpperCase(Locale.ROOT)
                + word.substring(first).toLowerCase(Locale.ROOT);
    }

    /** A one-token alias such as {@code bossa} → Bossa Nova must not grow a longer tag. */
    private static String collapseAdjacentDuplicates(String titled) {
        String[] words = titled.split(" ");
        List<String> parts = new ArrayList<>(words.length);
        String last = null;
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (last != null && last.equalsIgnoreCase(word)) {
                continue;
            }
            parts.add(word);
            last = word;
        }
        return String.join(" ", parts);
    }

    private static void indexGenre(String raw) {
        String display = titleCase(raw);
        putGenreKey(raw, display);
    }

    private static void aliasGenre(String from, String to) {
        String display = firstNonNull(GENRES.get(to.toLowerCase(Locale.ROOT)),
                foldGenre(to.toLowerCase(Locale.ROOT)), titleCase(to));
        putGenreKey(from, display);
        SYNONYMS.put(from.toLowerCase(Locale.ROOT), display);
    }

    private static void putGenreKey(String raw, String display) {
        String key = raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        GENRES.put(key, display);
        GENRES.put(key.replace('-', ' '), display);
        GENRES.put(key.replace(' ', '-'), display);
        SYNONYMS.put(key, display);
    }

    private static String foldGenre(String key) {
        if (key == null) {
            return null;
        }
        String spaced = key.replace('-', ' ');
        if (!spaced.equals(key) && GENRES.containsKey(spaced)) {
            return GENRES.get(spaced);
        }
        String dashed = key.replace(' ', '-');
        if (!dashed.equals(key) && GENRES.containsKey(dashed)) {
            return GENRES.get(dashed);
        }
        return null;
    }

    private static void putMood(String canonical, String... aliases) {
        MOODS.put(canonical.toLowerCase(Locale.ROOT), canonical);
        for (String alias : aliases) {
            MOODS.put(alias.toLowerCase(Locale.ROOT), canonical);
            SYNONYMS.put(alias.toLowerCase(Locale.ROOT), canonical);
        }
        SYNONYMS.put(canonical.toLowerCase(Locale.ROOT), canonical);
    }

    private static void alias(String from, String to) {
        SYNONYMS.put(from.toLowerCase(Locale.ROOT), to);
    }

    @SafeVarargs
    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
