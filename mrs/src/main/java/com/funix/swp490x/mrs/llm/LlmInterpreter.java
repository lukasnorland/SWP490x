package com.funix.swp490x.mrs.llm;

import java.util.Optional;

/**
 * Turns a curator prompt into catalog filter names.
 *
 * <p>Bean is {@link GeminiLlmInterpreter} when {@code mrs.llm.api-key} is set,
 * otherwise {@link VocabularyMatchInterpreter}.
 */
public interface LlmInterpreter {

    /**
     * @return named filters when the query maps onto the vocabulary; empty when
     *     nothing matched and Search should fall back to title/artist {@code LIKE}
     */
    Optional<InterpretedFilters> interpret(String query, FilterVocabulary vocabulary);
}
