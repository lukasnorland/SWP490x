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
 * Closed-enough vocabularies so Genre, Mood and Tags do not mix.
 *
 * <p>Parents stay on a song together with any known subgenre. Mood adjectives
 * leave Tags. Everything else is a freeform descriptor, Title Cased.
 */
public final class CatalogTaxonomy {

    /**
     * Coarse buckets the last remap collapsed to. A song may also carry a
     * more specific style in {@code genres}.
     */
    public static final List<String> PARENTS = List.of(
            "Electronic", "Pop", "Hip Hop", "Rock", "R&B", "Latin", "Folk",
            "Acoustic", "Cinematic", "Funk", "Country", "Ambient", "World",
            "Gospel", "Jazz", "Classical", "Metal", "Blues", "Reggae");

    private static final Pattern ERA = Pattern.compile("^(?:\\d{2}|\\d{4})s$", Pattern.CASE_INSENSITIVE);

    /** Last token (or last hyphen part) that usually means a musical style. */
    private static final Set<String> GENRE_FAMILY = Set.of(
            "pop", "rock", "hop", "house", "punk", "jazz", "metal", "folk", "funk",
            "soul", "disco", "trance", "rap", "reggae", "country", "blues", "gospel",
            "ambient", "latin", "edm", "techno", "garage", "phonk", "indie", "drill",
            "grime", "ska", "wave", "core", "dubstep", "hardstyle", "trap", "beats",
            "orchestra", "orchestral", "cumbia", "salsa", "samba", "bossa", "motown",
            "reggaeton", "afrobeats", "classical", "electronic", "acoustic",
            "cinematic", "world", "dancehall", "dance", "electro", "experimental",
            "industrial", "chiptune", "hyperpop", "complextro", "neurofunk",
            "gabber", "breakcore", "synthwave", "hardcore", "r&b", "swing",
            "lullaby", "choral", "filmi", "dangdut", "brega");

    private static final Set<String> NOT_GENRE = Set.of(
            "coffee house", "house party", "house on the hill", "soul mate",
            "soul horns", "soul strings", "bass guitar", "walking bass",
            "standup bass", "upright bass", "funky bass", "heavy bass",
            "synth bass", "saw bass", "distorted bass", "808 bass", "bass line",
            "train beat", "bossa beat", "latin percussion", "rock vocals",
            "big choir", "soul ballad");

    private static final Set<String> INSTRUMENT_MARKERS = Set.of(
            "guitar", "vocals", "vocal", "piano", "drums", "drum", "saxophone",
            "violin", "accordion", "trumpet", "keyboard", "strings", "percussion",
            "harmonica", "sitar", "choir", "brass", "synth", "synths", "guiro",
            "rapper", "bassline");

    private static final Map<String, String> GENRES = new HashMap<>();
    private static final Map<String, String> MOODS = new HashMap<>();
    private static final Map<String, String> SYNONYMS = new HashMap<>();

    static {
        for (String parent : PARENTS) {
            putGenre(parent);
        }
        putGenre("Hip Hop", "hip-hop", "hiphop", "hip hop", "hip-hop/rap", "hiphop/rap");
        putGenre("R&B", "rnb", "r and b", "r & b");

        putGenre("Afrobeats", "afro beats", "afro-beats");
        putGenre("Alternative", "alt");
        putGenre("Alternative Folk");
        putGenre("Alternative Hip Hop", "alternative hip-hop", "alt hip hop");
        putGenre("Alternative Pop", "alt-pop", "alt pop");
        putGenre("Alternative Rock", "alt-rock", "alt rock", "rock alternative");
        putGenre("Americana");
        putGenre("Bass House");
        putGenre("Beats", "beats");
        putGenre("Bedroom Pop");
        putGenre("Big Room House", "big room");
        putGenre("Blues Rock");
        putGenre("Bossa Nova", "bossa", "brazilian bossa nova");
        putGenre("Brazilian Funk", "funk carioca", "baile funk", "favela funk", "brega funk");
        putGenre("Breakcore");
        putGenre("Chiptune", "chip tune");
        putGenre("Complextro");
        putGenre("Contemporary Christian");
        putGenre("Contemporary Country");
        putGenre("Contemporary Folk");
        putGenre("Contemporary Gospel");
        putGenre("Contemporary R&B", "modern r&b", "pop-r&b", "pop r&b");
        putGenre("Country Pop", "country-pop");
        putGenre("Country Rock", "country-rock");
        putGenre("Cumbia");
        putGenre("Dance", "dance music");
        putGenre("Dance-Pop", "dance pop", "dance-pop");
        putGenre("Dancehall");
        putGenre("Deep House");
        putGenre("Disco");
        putGenre("Dream Pop");
        putGenre("Drill");
        putGenre("Drum And Bass", "drum and bass", "drum & bass", "dnb", "d&b",
                "liquid drum and bass");
        putGenre("Dubstep", "melodic dubstep");
        putGenre("EDM", "electronic dance music", "electronic dance music (edm)");
        putGenre("Electro");
        putGenre("Electro House", "electro-house");
        putGenre("Electropop", "electro-pop", "electro pop");
        putGenre("Synth-Pop", "synth-pop", "synth pop");
        putGenre("Eurodance", "euro-dance");
        putGenre("Experimental");
        putGenre("Filmi");
        putGenre("Folk Pop", "folk-pop");
        putGenre("Future Bass");
        putGenre("Future House");
        putGenre("Gabber");
        putGenre("Grime");
        putGenre("Happy Hardcore");
        putGenre("Hard Rock");
        putGenre("Hardcore");
        putGenre("Hardcore Punk");
        putGenre("Hardstyle");
        putGenre("House");
        putGenre("Hyperpop", "hyper pop");
        putGenre("Indie");
        putGenre("Indie Folk", "indie-folk");
        putGenre("Indie Pop", "indie-pop");
        putGenre("Indie Rock");
        putGenre("Industrial");
        putGenre("J-Core", "jcore");
        putGenre("J-Pop", "jpop", "j pop");
        putGenre("J-Rock", "jrock", "j rock");
        putGenre("K-Pop", "kpop", "k pop", "k-pop", "korean pop");
        putGenre("K-Rock", "krock", "k-rock", "korean rock");
        putGenre("Latin Pop", "latin-pop");
        putGenre("Lo-Fi", "lofi", "lo fi", "lo-fi");
        putGenre("Lo-Fi Hip Hop", "lo-fi hip hop", "lofi hip hop", "lo-fi hip-hop");
        putGenre("Melodic House");
        putGenre("Motown");
        putGenre("Neo Soul", "neo-soul");
        putGenre("Neurofunk");
        putGenre("Nu-Disco", "nu disco", "nu-disco");
        putGenre("Nu-Metal", "nu metal", "nu-metal");
        putGenre("Orchestral", "orchestra", "orchestral music");
        putGenre("Phonk");
        putGenre("Pop Punk", "pop-punk", "pop punk");
        putGenre("Pop Rock", "pop-rock", "poprock");
        putGenre("Progressive House", "prog house");
        putGenre("Psychedelic Rock");
        putGenre("Punk", "punk rock");
        putGenre("Rap");
        putGenre("Reggaeton");
        putGenre("Roots Rock");
        putGenre("Salsa");
        putGenre("Samba");
        putGenre("Singer-Songwriter", "singer songwriter", "singer-songwriter");
        putGenre("Soft House");
        putGenre("Soul");
        putGenre("Synthwave", "synth wave");
        putGenre("Tech House", "tech-house");
        putGenre("Techno");
        putGenre("Trance");
        putGenre("Trap");
        putGenre("Trip Hop", "trip-hop", "triphop");
        putGenre("Tropical House");
        putGenre("UK Drill");
        putGenre("UK Garage", "ukg");
        putGenre("West Coast Hip Hop", "west coast hip-hop", "west coast rap");

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
        alias("nu-disco", "Nu-Disco");
        alias("nu disco", "Nu-Disco");
        alias("nu-metal", "Nu-Metal");
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
        String known = firstNonNull(SYNONYMS.get(key), GENRES.get(key), MOODS.get(key));
        if (known != null) {
            return known;
        }
        return titleCase(cleaned);
    }

    /**
     * Sends every name to one vocabulary. A name listed under the wrong JSON
     * field still lands in Genre or Mood when the taxonomy knows it.
     */
    public Buckets classify(List<String> genres, List<String> moods, List<String> tags) {
        LinkedHashSet<String> outGenres = new LinkedHashSet<>();
        LinkedHashSet<String> outMoods = new LinkedHashSet<>();
        LinkedHashSet<String> outTags = new LinkedHashSet<>();
        Set<String> seen = new HashSet<>();
        consume(genres, TagType.GENRE, outGenres, outMoods, outTags, seen);
        consume(moods, TagType.MOOD, outGenres, outMoods, outTags, seen);
        consume(tags, TagType.TAGS, outGenres, outMoods, outTags, seen);
        return new Buckets(List.copyOf(outGenres), List.copyOf(outMoods), List.copyOf(outTags));
    }

    /**
     * Where a lone name belongs. Tags that are known styles or feelings move;
     * an unknown mood already on the song stays a mood.
     */
    public TagType typeOf(String displayName) {
        return route(displayName, TagType.TAGS);
    }

    private void consume(List<String> names, TagType source, Set<String> genres, Set<String> moods,
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
            switch (route(display, source)) {
                case GENRE -> genres.add(display);
                case MOOD -> moods.add(display);
                default -> tags.add(display);
            }
        }
    }

    private TagType route(String display, TagType source) {
        if (ERA.matcher(display).matches()) {
            return TagType.TAGS;
        }
        String key = display.toLowerCase(Locale.ROOT);
        if (GENRES.containsKey(key) || looksLikeGenre(display)) {
            return TagType.GENRE;
        }
        if (MOODS.containsKey(key)) {
            return TagType.MOOD;
        }
        if (source == TagType.MOOD) {
            return TagType.MOOD;
        }
        if (source == TagType.GENRE) {
            return TagType.GENRE;
        }
        return TagType.TAGS;
    }

    private boolean looksLikeGenre(String display) {
        String lower = display.toLowerCase(Locale.ROOT);
        if (NOT_GENRE.contains(lower) || isInstrumentish(lower)) {
            return false;
        }
        String last = lastStyleToken(lower);
        return GENRE_FAMILY.contains(last);
    }

    private static boolean isInstrumentish(String lower) {
        if ("bass".equals(lower) || "beat".equals(lower)) {
            return true;
        }
        for (String marker : INSTRUMENT_MARKERS) {
            if (lower.equals(marker) || lower.endsWith(" " + marker)
                    || lower.contains(" " + marker + " ")) {
                return true;
            }
        }
        return false;
    }

    private static String lastStyleToken(String lower) {
        int space = lower.lastIndexOf(' ');
        String lastWord = space < 0 ? lower : lower.substring(space + 1);
        int hyphen = lastWord.lastIndexOf('-');
        return hyphen < 0 ? lastWord : lastWord.substring(hyphen + 1);
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
        return String.join(" ", parts);
    }

    private static String titleToken(String word) {
        if (word.isEmpty()) {
            return word;
        }
        String folded = SYNONYMS.get(word.toLowerCase(Locale.ROOT));
        if (folded != null) {
            return folded;
        }
        int first = word.offsetByCodePoints(0, 1);
        return word.substring(0, first).toUpperCase(Locale.ROOT)
                + word.substring(first).toLowerCase(Locale.ROOT);
    }

    private static void putGenre(String canonical, String... aliases) {
        GENRES.put(canonical.toLowerCase(Locale.ROOT), canonical);
        for (String alias : aliases) {
            GENRES.put(alias.toLowerCase(Locale.ROOT), canonical);
            SYNONYMS.put(alias.toLowerCase(Locale.ROOT), canonical);
        }
        SYNONYMS.put(canonical.toLowerCase(Locale.ROOT), canonical);
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
