package com.funix.swp490x.mrs.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.funix.swp490x.mrs.catalog.CatalogTaxonomy;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VocabularyMatchInterpreterTest {

    private final VocabularyMatchInterpreter interpreter =
            new VocabularyMatchInterpreter(new CatalogTaxonomy());

    @Test
    void mapsSynonymsAndUsedTags() {
        FilterVocabulary vocab = FilterVocabulary.of(
                List.of(new Tag(TagType.TAGS, "summer")), new CatalogTaxonomy());

        Optional<InterpretedFilters> result = interpreter.interpret(
                "energetic summer electronic", vocab);

        assertThat(result).isPresent();
        assertThat(result.get().moods()).contains("Energetic");
        assertThat(result.get().tags()).contains("summer");
        assertThat(result.get().genres()).anyMatch(name -> name.toLowerCase().contains("electronic"));
    }

    @Test
    void emptyWhenNothingMatches() {
        FilterVocabulary vocab = FilterVocabulary.of(List.of(), new CatalogTaxonomy());

        assertThat(interpreter.interpret("zzzz not a real genre xyzzy", vocab)).isEmpty();
    }

    @Test
    void mapsUpbeatPopSummerRelaxAsSeparateChips() {
        FilterVocabulary vocab = FilterVocabulary.of(
                List.of(new Tag(TagType.TAGS, "summer")), new CatalogTaxonomy());

        Optional<InterpretedFilters> result = interpreter.interpret(
                "upbeat pop summer relax", vocab);

        assertThat(result).isPresent();
        assertThat(result.get().moods()).contains("Upbeat", "Relaxing");
        assertThat(result.get().genres()).contains("Pop");
        assertThat(result.get().tags()).contains("summer");
    }
}
