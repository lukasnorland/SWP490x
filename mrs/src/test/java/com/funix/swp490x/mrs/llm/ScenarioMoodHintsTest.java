package com.funix.swp490x.mrs.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.funix.swp490x.mrs.catalog.CatalogTaxonomy;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScenarioMoodHintsTest {

    @Test
    void summerSeasonSuggestsUpbeatMoods() {
        FilterVocabulary vocab = FilterVocabulary.of(
                List.of(new Tag(TagType.TAGS, "Summer")), new CatalogTaxonomy());

        assertThat(ScenarioMoodHints.suggest(
                "Suggest for me a playlist for a Summer season", vocab))
                .contains("Upbeat", "Carefree", "Vibrant", "Happy");
    }

    @Test
    void winterFestivalSuggestsCelebratoryAndCozyMoods() {
        FilterVocabulary vocab = FilterVocabulary.of(
                List.of(
                        new Tag(TagType.TAGS, "Winter"),
                        new Tag(TagType.TAGS, "Festival")),
                new CatalogTaxonomy());

        List<String> moods = ScenarioMoodHints.suggest(
                "Suggest for me a playlist for a Winter festival", vocab);

        assertThat(moods).contains("Celebratory", "Upbeat", "Cozy");
    }

    @Test
    void brokenHeartSuggestsMelancholicMoods() {
        FilterVocabulary vocab = FilterVocabulary.of(
                List.of(new Tag(TagType.TAGS, "Broken Heart")), new CatalogTaxonomy());

        assertThat(ScenarioMoodHints.suggest("A playlist for a broken heart", vocab))
                .contains("Melancholic", "Sad", "Heartfelt", "Bittersweet");
    }
}
