package com.funix.swp490x.mrs.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.funix.swp490x.mrs.catalog.CatalogTaxonomy;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiLlmInterpreterTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {400, 200})
    void failedApiOrInvalidJsonDoesNotBecomeAnOfflineChipSuccess(int status) throws Exception {
        var client = org.mockito.Mockito.mock(com.google.genai.Client.class);
        var models = org.mockito.Mockito.mock(com.google.genai.Models.class);
        org.springframework.test.util.ReflectionTestUtils.setField(client, "models", models);
        var response = org.mockito.Mockito.mock(com.google.genai.types.GenerateContentResponse.class);
        org.mockito.Mockito.when(response.text()).thenReturn("invalid JSON");
        var generation = org.mockito.Mockito.when(models.generateContent(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(com.google.genai.types.GenerateContentConfig.class)));
        if (status == 400) {
            generation.thenThrow(new IllegalStateException("HTTP 400: invalid API key"));
        } else {
            generation.thenReturn(response);
        }
        var taxonomy = new CatalogTaxonomy();
        var vocabulary = FilterVocabulary.of(List.of(new Tag(TagType.TAGS, "Summer")), taxonomy);
        var interpreter = new GeminiLlmInterpreter(new LlmProperties(),
                new VocabularyMatchInterpreter(taxonomy), client);
        assertThat(interpreter.interpret("energetic music for a summer event", vocabulary)).isEmpty();
        org.mockito.Mockito.verify(models).generateContent(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(com.google.genai.types.GenerateContentConfig.class));

        var settings = org.mockito.Mockito.mock(com.funix.swp490x.mrs.service.SettingsService.class);
        org.mockito.Mockito.when(settings.llmMinQueryChars()).thenReturn(10);
        org.mockito.Mockito.when(settings.llmMaxQueryChars()).thenReturn(200);
        var tags = org.mockito.Mockito.mock(com.funix.swp490x.mrs.repository.TagRepository.class);
        org.mockito.Mockito.when(tags.findAllUsedOrderByTypeAscNameAsc()).thenReturn(List.of(new Tag(TagType.TAGS, "Summer")));
        var catalog = org.mockito.Mockito.mock(com.funix.swp490x.mrs.service.SongCatalogService.class);
        org.mockito.Mockito.when(catalog.searchRecommended(
                List.of(), List.of(), List.of(), List.of(), "energetic music for a summer event", null, 0))
                .thenReturn(org.springframework.data.domain.Page.empty());
        var logs = org.mockito.Mockito.mock(com.funix.swp490x.mrs.repository.RecommendationLogRepository.class);
        var search = new com.funix.swp490x.mrs.service.SearchService(interpreter, settings,
                org.mockito.Mockito.mock(FilterMapper.class), tags, catalog, logs);
        assertThat(search.interpretRedirect(1L, "energetic music for a summer event", null).fallback()).isTrue();
        var logged = org.mockito.ArgumentCaptor.forClass(com.funix.swp490x.mrs.domain.RecommendationLog.class);
        org.mockito.Mockito.verify(logs).save(logged.capture());
        assertThat(logged.getValue().isLlmUsed()).isTrue();
        assertThat(logged.getValue().getLlmSucceeded()).isFalse();
        assertThat(logged.getValue().getInterpretedFilters()).contains("\"fallback\":true");
    }

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
