package com.funix.swp490x.mrs.llm;

import com.funix.swp490x.mrs.catalog.CatalogTaxonomy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    /**
     * Gemini when {@code mrs.llm.api-key} is set; vocabulary matching otherwise.
     * Gemini failures still fall back inside {@link GeminiLlmInterpreter}.
     */
    @Bean
    public LlmInterpreter llmInterpreter(LlmProperties properties) {
        VocabularyMatchInterpreter fallback =
                new VocabularyMatchInterpreter(new CatalogTaxonomy());
        if (StringUtils.hasText(properties.getApiKey())) {
            return new GeminiLlmInterpreter(properties, fallback);
        }
        return fallback;
    }
}
