package com.funix.swp490x.mrs.llm;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Contextual-search defaults (FT-04); the API key stays in local configuration.
 * Without a key, use vocabulary matching; failed Gemini calls trigger keyword search.
 */
@ConfigurationProperties("mrs.llm")
public class LlmProperties {

    private String provider = "gemini";

    private String model = "gemini-3.8-flash";

    /** Empty until Gemini is enabled. Never commit a real value. */
    private String apiKey = "";

    /** Default timeout; live calls use System Settings when available (UC-31). */
    private Duration timeout = Duration.ofSeconds(30);

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
