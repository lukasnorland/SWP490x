package com.funix.swp490x.mrs.llm;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Direct Gemini API smoke test. Run:
 * {@code mvn -Dtest=GeminiApiSmokeTest test}
 *
 * <p>Set {@code mrs.llm.api-key} in {@code local.properties} first.
 */
class GeminiApiSmokeTest {

    private static String apiKey = "";
    private static String model = "gemini-2.5-flash";
    private static Duration timeout = Duration.ofSeconds(30);

    @BeforeAll
    static void loadConfig() throws Exception {
        Properties props = new Properties();
        Path local = Path.of("local.properties");
        if (Files.isRegularFile(local)) {
            try (var reader = Files.newBufferedReader(local)) {
                props.load(reader);
            }
        }
        try (InputStream in = GeminiApiSmokeTest.class.getResourceAsStream("/application.properties")) {
            if (in != null) {
                props.load(in);
            }
        }
        apiKey = props.getProperty("mrs.llm.api-key", "").trim();
        model = props.getProperty("mrs.llm.model", model).trim();
        String timeoutRaw = props.getProperty("mrs.llm.timeout", "30s").trim();
        if (timeoutRaw.endsWith("s")) {
            timeout = Duration.ofSeconds(Long.parseLong(timeoutRaw.substring(0, timeoutRaw.length() - 1)));
        }
        String envKey = System.getenv("MRS_LLM_API_KEY");
        if (apiKey.isBlank() && envKey != null && !envKey.isBlank()) {
            apiKey = envKey.trim();
        }
    }

    static boolean apiKeyConfigured() {
        return !apiKey.isBlank();
    }

    @Test
    @EnabledIf("apiKeyConfigured")
    void plainTextPing() {
        Client client = Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder().timeout((int) timeout.toMillis()).build())
                .build();
        try (client) {
            long started = System.currentTimeMillis();
            GenerateContentResponse response = client.models.generateContent(
                    model,
                    "Reply with exactly one word: pong",
                    GenerateContentConfig.builder().build());
            long elapsed = System.currentTimeMillis() - started;
            String text = response.text();
            System.out.println("PLAIN PING model=" + model + " elapsedMs=" + elapsed + " text=" + text);
            org.assertj.core.api.Assertions.assertThat(text).isNotBlank();
        }
    }

    @Test
    @EnabledIf("apiKeyConfigured")
    void structuredMoodJsonForSummerSeason() {
        Schema schema = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "moods", Schema.builder()
                                .type(Type.Known.ARRAY)
                                .items(Schema.builder().type(Type.Known.STRING).build())
                                .build()))
                .build();
        Client client = Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder().timeout((int) timeout.toMillis()).build())
                .build();
        try (client) {
            long started = System.currentTimeMillis();
            GenerateContentResponse response = client.models.generateContent(
                    model,
                    """
                    Pick 2-4 moods for a summer season playlist from this list only:
                    Celebratory, Upbeat, Energetic, Uplifting, Calm, Relaxing
                    Query: Suggest for me a playlist for a Summer season
                    """,
                    GenerateContentConfig.builder()
                            .responseMimeType("application/json")
                            .responseSchema(schema)
                            .build());
            long elapsed = System.currentTimeMillis() - started;
            String text = response.text();
            System.out.println("STRUCTURED model=" + model + " elapsedMs=" + elapsed + " json=" + text);
            org.assertj.core.api.Assertions.assertThat(text).contains("mood");
        }
    }
}
