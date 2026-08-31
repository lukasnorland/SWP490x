package com.funix.swp490x.mrs.llm;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How contextual Search talks to an LLM (FT-04). The key stays out of git —
 * empty here, set in {@code local.properties} when Gemini is wired.
 *
 * <p>This slice does not call a provider. {@code api-key} is ignored until a
 * later {@code GeminiLlmInterpreter} reads it.
 */
@ConfigurationProperties("mrs.llm")
public class LlmProperties {

    private String provider = "gemini";

    private String model = "gemini-2.5-flash";

    /** Empty until Gemini is enabled. Never commit a real value. */
    private String apiKey = "";

    /** FT-04 interpretation budget. */
    private Duration timeout = Duration.ofSeconds(5);

    private int minQueryChars = 10;

    private int maxQueryChars = 200;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMinQueryChars() {
        return minQueryChars;
    }

    public void setMinQueryChars(int minQueryChars) {
        this.minQueryChars = minQueryChars;
    }

    public int getMaxQueryChars() {
        return maxQueryChars;
    }

    public void setMaxQueryChars(int maxQueryChars) {
        this.maxQueryChars = maxQueryChars;
    }
}
