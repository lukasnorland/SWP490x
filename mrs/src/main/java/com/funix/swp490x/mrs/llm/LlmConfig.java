package com.funix.swp490x.mrs.llm;

import com.funix.swp490x.mrs.catalog.CatalogTaxonomy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    /**
     * Vocabulary matching only. A later slice swaps this for Gemini when
     * {@code mrs.llm.api-key} is set, keeping this implementation as fallback.
     */
    @Bean
    public LlmInterpreter llmInterpreter() {
        return new VocabularyMatchInterpreter(new CatalogTaxonomy());
    }
}
