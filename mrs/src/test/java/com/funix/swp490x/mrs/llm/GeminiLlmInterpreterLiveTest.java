package com.funix.swp490x.mrs.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.funix.swp490x.mrs.catalog.CatalogTaxonomy;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import tools.jackson.databind.json.JsonMapper;

/**
 * Manual live Gemini check. Run with:
 * {@code mvn -Dtest=GeminiLlmInterpreterLiveTest test}
 */
class GeminiLlmInterpreterLiveTest {

    private static LlmProperties properties;
    private static boolean enabled;

    @BeforeAll
    static void loadProperties() throws Exception {
        properties = new LlmProperties();
        Properties file = new Properties();
        try (InputStream in = GeminiLlmInterpreterLiveTest.class.getResourceAsStream(
                "/application.properties")) {
            if (in != null) {
                file.load(in);
            }
        }
        String apiKey = file.getProperty("mrs.llm.api-key", "").trim();
        if (apiKey.isBlank()) {
            java.nio.file.Path local = java.nio.file.Path.of("local.properties");
            if (java.nio.file.Files.isRegularFile(local)) {
                Properties localProps = new Properties();
                try (var reader = java.nio.file.Files.newBufferedReader(local)) {
                    localProps.load(reader);
                }
                apiKey = localProps.getProperty("mrs.llm.api-key", "").trim();
            }
        }
        enabled = !apiKey.isBlank();
        if (enabled) {
            properties.setApiKey(apiKey);
            properties.setModel(file.getProperty("mrs.llm.model", "gemini-2.5-flash").trim());
            properties.setTimeout(Duration.ofSeconds(30));
        }
    }

    static boolean apiKeyConfigured() {
        return enabled;
    }

    @Test
    @EnabledIf("apiKeyConfigured")
    void summerSeasonPromptShouldInferMoods() {
        FilterVocabulary vocab = FilterVocabulary.of(
                List.of(
                        new Tag(TagType.TAGS, "Summer"),
                        new Tag(TagType.TAGS, "Festival")),
                new CatalogTaxonomy());
        GeminiLlmInterpreter interpreter = new GeminiLlmInterpreter(
                properties, new VocabularyMatchInterpreter(new CatalogTaxonomy()));

        Optional<InterpretedFilters> result = interpreter.interpret(
                "Suggest for me a playlist for a Summer season", vocab);

        System.out.println("LIVE RESULT: " + result);
        assertThat(result).isPresent();
        System.out.println("MOODS: " + result.get().moods());
        System.out.println("TAGS: " + result.get().tags());
        assertThat(result.get().moods())
                .as("scenario prompt should infer moods, not only literal tags")
                .isNotEmpty();
    }

    @Test
    void moodRetryJsonParses() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        var parsed = mapper.readValue("{\"moods\":[\"Upbeat\",\"Celebratory\"]}", MoodRetry.class);
        assertThat(parsed.moods()).containsExactly("Upbeat", "Celebratory");
    }

    private record MoodRetry(List<String> moods) {
    }
}
