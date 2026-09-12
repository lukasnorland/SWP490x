package com.funix.swp490x.mrs.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.funix.swp490x.mrs.catalog.CatalogTaxonomy;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiLlmInterpreterTest {

    @Test
    void buildPromptOmitsTagsAndListsMoodsFirst() {
        FilterVocabulary vocab = FilterVocabulary.of(
                List.of(
                        new Tag(TagType.TAGS, "summer"),
                        new Tag(TagType.GENRE, "Pop")),
                new CatalogTaxonomy());

        String prompt = GeminiLlmInterpreter.buildPrompt(
                "upbeat pop summer relax", vocab);

        assertThat(prompt).contains("Query: upbeat pop summer relax");
        assertThat(prompt).contains("Moods:");
        assertThat(prompt).contains("Genres on songs:");
        assertThat(prompt).contains("Pop");
        assertThat(prompt).doesNotContain("Tags on songs:");
        assertThat(prompt).contains("Do not return tags");
    }

    @Test
    void mergeCombinesLiteralTagsWithGeminiMoods() {
        InterpretedFilters gemini = new InterpretedFilters(
                List.of(), List.of("Upbeat", "Celebratory"), List.of(), List.of(), 0.8);
        InterpretedFilters literal = new InterpretedFilters(
                List.of(), List.of(), List.of(), List.of("Summer"), null);

        InterpretedFilters merged = GeminiLlmInterpreter.merge(gemini, literal);

        assertThat(merged.moods()).containsExactly("Upbeat", "Celebratory");
        assertThat(merged.tags()).containsExactly("Summer");
    }

    @Test
    void sanitizeDropsUnknownNames() {
        FilterVocabulary vocab = FilterVocabulary.of(
                List.of(new Tag(TagType.MOOD, "Energetic")), new CatalogTaxonomy());

        InterpretedFilters sanitized = GeminiLlmInterpreter.sanitize(
                new InterpretedFilters(
                        List.of("Pop", "Made Up Genre"),
                        List.of("energetic", "Unknown Mood"),
                        List.of("Nobody"),
                        List.of(),
                        0.9),
                vocab);

        assertThat(sanitized.genres()).containsExactly("Pop");
        assertThat(sanitized.moods()).containsExactly("Energetic");
        assertThat(sanitized.artists()).isEmpty();
        assertThat(sanitized.confidence()).isEqualTo(0.9);
    }

    @Test
    void mergeDropsEchoTagsWhenSemanticMoodsPresent() {
        InterpretedFilters semantic = new InterpretedFilters(
                List.of(), List.of("Melancholic", "Sad"), List.of(), List.of(), null);
        InterpretedFilters literal = new InterpretedFilters(
                List.of(), List.of(), List.of(), List.of("Broken Heart"), null);

        InterpretedFilters merged = GeminiLlmInterpreter.mergeForQuery(
                "A playlist for a broken heart", semantic, literal);

        assertThat(merged.moods()).contains("Melancholic", "Sad");
        assertThat(merged.tags()).isEmpty();
    }

    @Test
    void mergeKeepsEchoTagsWhenNoSemanticSignal() {
        InterpretedFilters semantic = InterpretedFilters.empty();
        InterpretedFilters literal = new InterpretedFilters(
                List.of(), List.of(), List.of(), List.of("Broken Heart"), null);

        InterpretedFilters merged = GeminiLlmInterpreter.mergeForQuery(
                "A playlist for a broken heart", semantic, literal);

        assertThat(merged.tags()).containsExactly("Broken Heart");
    }

    @Test
    void summerSeasonOfflinePathAddsMoodsAndDropsEchoTag() {
        FilterVocabulary vocab = FilterVocabulary.of(
                List.of(new Tag(TagType.TAGS, "Summer")), new CatalogTaxonomy());
        InterpretedFilters literal = new VocabularyMatchInterpreter(new CatalogTaxonomy())
                .interpret("Suggest for me a playlist for a Summer season", vocab)
                .orElseThrow();
        InterpretedFilters hinted = new InterpretedFilters(
                List.of(),
                ScenarioMoodHints.suggest(
                        "Suggest for me a playlist for a Summer season", vocab),
                List.of(),
                List.of(),
                null);
        InterpretedFilters merged = GeminiLlmInterpreter.mergeForQuery(
                "Suggest for me a playlist for a Summer season", hinted, literal);

        assertThat(merged.tags()).isEmpty();
        assertThat(merged.moods()).isNotEmpty();
    }

    @Test
    void createClientUsesConfiguredTimeout() {
        assertThat(GeminiLlmInterpreter.createClient("test-key", Duration.ofSeconds(5))).isNotNull();
    }
}
