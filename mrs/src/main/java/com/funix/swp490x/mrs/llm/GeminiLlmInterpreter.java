package com.funix.swp490x.mrs.llm;

import com.funix.swp490x.mrs.service.SettingsService;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * FT-04 Gemini adapter: structured JSON in, {@link InterpretedFilters} out.
 * Literal tag tokens are merged from {@link VocabularyMatchInterpreter}; Gemini
 * focuses on moods and genres. Timeout or bad JSON falls through to offline
 * {@link ScenarioMoodHints} plus vocabulary tags (BR-07).
 */
public class GeminiLlmInterpreter implements LlmInterpreter, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmInterpreter.class);

    private static final Schema RESPONSE_SCHEMA = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(Map.of(
                    "genres", stringArraySchema(),
                    "moods", stringArraySchema(),
                    "artists", stringArraySchema(),
                    "confidence", Schema.builder().type(Type.Known.NUMBER).build()))
            .build();

    private static final Schema MOOD_RETRY_SCHEMA = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(Map.of("moods", stringArraySchema()))
            .build();

    private volatile Client client;
    private final LlmProperties properties;
    private final SettingsService settings;
    private final LlmInterpreter fallback;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private volatile Duration builtTimeout;

    public GeminiLlmInterpreter(LlmProperties properties, LlmInterpreter fallback) {
        this(properties, fallback, null, createClient(properties.getApiKey(), properties.getTimeout()));
    }

    public GeminiLlmInterpreter(LlmProperties properties, LlmInterpreter fallback,
            SettingsService settings) {
        this(properties, fallback, settings, createClient(properties.getApiKey(),
                settings != null ? settings.llmTimeout() : properties.getTimeout()));
    }

    GeminiLlmInterpreter(LlmProperties properties, LlmInterpreter fallback, Client client) {
        this(properties, fallback, null, client);
    }

    private GeminiLlmInterpreter(LlmProperties properties, LlmInterpreter fallback,
            SettingsService settings, Client client) {
        this.properties = properties;
        this.fallback = fallback;
        this.settings = settings;
        this.client = client;
        this.builtTimeout = settings != null ? settings.llmTimeout() : properties.getTimeout();
    }

    @Override
    public Optional<InterpretedFilters> interpret(String query, FilterVocabulary vocabulary) {
        if (!StringUtils.hasText(query) || vocabulary == null) {
            return Optional.empty();
        }
        String trimmed = query.trim();
        InterpretedFilters literal = fallback.interpret(trimmed, vocabulary)
                .orElse(InterpretedFilters.empty());
        try {
            InterpretedFilters gemini = interpretWithGemini(trimmed, vocabulary);
            InterpretedFilters merged = mergeForQuery(trimmed, gemini, literal);
            return merged.isEmpty() ? Optional.empty() : Optional.of(merged);
        } catch (RuntimeException e) {
            log.warn("Gemini interpretation failed; using offline mood hints and vocabulary tags", e);
            InterpretedFilters offline = mergeForQuery(trimmed,
                    offlineMoods(trimmed, vocabulary), literal);
            return offline.isEmpty() ? Optional.empty() : Optional.of(offline);
        }
    }

    private InterpretedFilters interpretWithGemini(String query, FilterVocabulary vocabulary) {
        String text = generateJson(buildPrompt(query, vocabulary), RESPONSE_SCHEMA);
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException("Gemini returned no text");
        }
        InterpretedFilters parsed = jsonMapper.readValue(text, InterpretedFilters.class);
        InterpretedFilters sanitized = sanitize(parsed, vocabulary);
        if (sanitized.moods().isEmpty()) {
            sanitized = retryMoods(query, vocabulary, sanitized);
        }
        if (sanitized.moods().isEmpty()) {
            sanitized = merge(sanitized, offlineMoods(query, vocabulary));
        }
        return sanitized;
    }

    private InterpretedFilters retryMoods(String query, FilterVocabulary vocabulary,
            InterpretedFilters base) {
        log.info("Gemini returned no moods; requesting mood-focused follow-up");
        try {
            String moodJson = generateJson(buildMoodRetryPrompt(query, vocabulary), MOOD_RETRY_SCHEMA);
            if (!StringUtils.hasText(moodJson)) {
                return base;
            }
            MoodRetry parsed = jsonMapper.readValue(moodJson, MoodRetry.class);
            List<String> moods = keepKnown(parsed.moods(), vocabulary.moods().values());
            if (moods.isEmpty()) {
                return base;
            }
            return new InterpretedFilters(
                    base.genres(), moods, base.artists(), List.of(), base.confidence());
        } catch (RuntimeException e) {
            log.warn("Gemini mood retry failed", e);
            return base;
        }
    }

    private static InterpretedFilters offlineMoods(String query, FilterVocabulary vocabulary) {
        List<String> moods = ScenarioMoodHints.suggest(query, vocabulary);
        if (moods.isEmpty()) {
            return InterpretedFilters.empty();
        }
        return new InterpretedFilters(List.of(), moods, List.of(), List.of(), null);
    }

    @Override
    public void destroy() {
        Client current = client;
        if (current != null) {
            current.close();
        }
    }

    static Client createClient(String apiKey, Duration timeout) {
        Duration budget = timeout == null ? Duration.ofSeconds(30) : timeout;
        return Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder()
                        .timeout((int) budget.toMillis())
                        .build())
                .build();
    }

    private Client currentClient() {
        if (settings == null) {
            return client;
        }
        Duration timeout = settings.llmTimeout();
        if (timeout.equals(builtTimeout)) {
            return client;
        }
        synchronized (this) {
            if (timeout.equals(builtTimeout)) {
                return client;
            }
            Client previous = client;
            Client next = createClient(properties.getApiKey(), timeout);
            client = next;
            builtTimeout = timeout;
            if (previous != null) {
                previous.close();
            }
            return next;
        }
    }

    static String buildPrompt(String query, FilterVocabulary vocabulary) {
        return """
                You map a curator's playlist-need prompt onto catalog metadata filters.

                Infer moods and genres from context, scenario, and tone -- not only words in the query.
                Scenario prompts MUST include 2-4 moods when any fit.
                Examples:
                - "summer festival" -> Celebratory, Upbeat, Energetic, Uplifting
                - "wind down after work" -> Calm, Relaxing, Mellow
                - "playlist for a broken heart" -> Melancholic, Sad, Heartfelt, Bittersweet

                Rules:
                - Return ONLY names from the lists below
                - Do not invent genres, moods, or artists
                - Do not return tags (literal tag matching is handled separately)
                - Use empty arrays when nothing fits
                - confidence is optional (0.0-1.0)

                Moods: %s
                Genres on songs: %s
                Artists on songs: %s

                Query: %s
                """.formatted(
                joinNames(vocabulary.moods().values()),
                joinNames(vocabulary.catalogGenreNames()),
                joinNames(vocabulary.artists().values()),
                query);
    }

    static String buildMoodRetryPrompt(String query, FilterVocabulary vocabulary) {
        return """
                Pick 2-4 moods from the list below that best match this playlist scenario.
                Return mood names only from this list.

                Moods: %s

                Query: %s
                """.formatted(joinNames(vocabulary.moods().values()), query);
    }

    static InterpretedFilters mergeForQuery(String query, InterpretedFilters semantic,
            InterpretedFilters literal) {
        boolean hasSemantic = !semantic.moods().isEmpty() || !semantic.genres().isEmpty();
        if (!hasSemantic) {
            return merge(semantic, literal);
        }
        InterpretedFilters literalWithoutEcho = new InterpretedFilters(
                literal.genres(),
                literal.moods(),
                literal.artists(),
                withoutEchoTags(query, literal.tags()),
                literal.confidence());
        return merge(semantic, literalWithoutEcho);
    }

    static List<String> withoutEchoTags(String query, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        String padded = " " + FilterVocabulary.normalize(query) + " ";
        List<String> kept = new ArrayList<>();
        for (String tag : tags) {
            if (!StringUtils.hasText(tag)) {
                continue;
            }
            String key = " " + FilterVocabulary.normalize(tag) + " ";
            if (!padded.contains(key)) {
                kept.add(tag);
            }
        }
        return List.copyOf(kept);
    }

    static InterpretedFilters merge(InterpretedFilters primary, InterpretedFilters secondary) {
        return new InterpretedFilters(
                union(primary.genres(), secondary.genres()),
                union(primary.moods(), secondary.moods()),
                union(primary.artists(), secondary.artists()),
                union(primary.tags(), secondary.tags()),
                primary.confidence() != null ? primary.confidence() : secondary.confidence());
    }

    private static List<String> union(List<String> first, List<String> second) {
        Set<String> merged = new LinkedHashSet<>();
        if (first != null) {
            merged.addAll(first);
        }
        if (second != null) {
            merged.addAll(second);
        }
        return List.copyOf(merged);
    }

    private String generateJson(String prompt, Schema schema) {
        GenerateContentResponse response = currentClient().models.generateContent(
                settings != null ? settings.llmModel() : properties.getModel(),
                prompt,
                GenerateContentConfig.builder()
                        .responseMimeType("application/json")
                        .responseSchema(schema)
                        .build());
        return response.text();
    }

    private static Schema stringArraySchema() {
        return Schema.builder()
                .type(Type.Known.ARRAY)
                .items(Schema.builder().type(Type.Known.STRING).build())
                .build();
    }

    private static String joinNames(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return "(none)";
        }
        return String.join(", ", names);
    }

    static InterpretedFilters sanitize(InterpretedFilters parsed, FilterVocabulary vocabulary) {
        if (parsed == null) {
            return InterpretedFilters.empty();
        }
        return new InterpretedFilters(
                keepKnown(parsed.genres(), vocabulary.genres().values()),
                keepKnown(parsed.moods(), vocabulary.moods().values()),
                keepKnown(parsed.artists(), vocabulary.artists().values()),
                keepKnown(parsed.tags(), vocabulary.tags().values()),
                parsed.confidence());
    }

    private static List<String> keepKnown(List<String> names, Collection<String> allowed) {
        Map<String, String> byKey = new LinkedHashMap<>();
        for (String name : allowed) {
            byKey.putIfAbsent(FilterVocabulary.normalize(name), name);
        }
        List<String> kept = new ArrayList<>();
        if (names == null) {
            return List.of();
        }
        for (String name : names) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String canonical = byKey.get(FilterVocabulary.normalize(name));
            if (canonical != null && !kept.contains(canonical)) {
                kept.add(canonical);
            }
        }
        return List.copyOf(kept);
    }

    private record MoodRetry(List<String> moods) {
    }
}
