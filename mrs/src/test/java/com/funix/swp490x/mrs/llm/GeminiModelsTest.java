package com.funix.swp490x.mrs.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeminiModelsTest {

    @Test
    void defaultModelIsOnTheList() {
        assertThat(GeminiModels.isKnown("gemini-3.8-flash")).isTrue();
        assertThat(GeminiModels.OPTIONS.getFirst().id()).isEqualTo("gemini-3.8-flash");
    }

    @Test
    void forFormKeepsASavedIdThatIsNoLongerListed() {
        assertThat(GeminiModels.forForm("gemini-1.5-flash"))
                .first()
                .extracting(GeminiModels.Option::id)
                .isEqualTo("gemini-1.5-flash");
    }

    @Test
    void movingLatestAliasesAreNotListed() {
        assertThat(GeminiModels.isKnown("gemini-flash-latest")).isFalse();
        assertThat(GeminiModels.isKnown("gemini-flash-lite-latest")).isFalse();
        assertThat(GeminiModels.isKnown("gemini-pro-latest")).isFalse();
    }
}
