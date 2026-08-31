package com.funix.swp490x.mrs.llm;

import java.util.Optional;

/**
 * Turns a curator prompt into catalog filter names. Gemini will implement this
 * later; vocabulary matching is the BR-07 fallback and the only bean for now.
 */
public interface LlmInterpreter {

    /**
     * @return named filters when the query maps onto the vocabulary; empty when
     *     nothing matched and Search should fall back to title/artist {@code LIKE}
     */
    Optional<InterpretedFilters> interpret(String query, FilterVocabulary vocabulary);
}
