package com.funix.swp490x.mrs.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.funix.swp490x.mrs.catalog.CatalogTaxonomy.Buckets;
import com.funix.swp490x.mrs.domain.TagType;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogTaxonomyTest {

    private final CatalogTaxonomy taxonomy = new CatalogTaxonomy();

    @Test
    void titleCasesAndFoldsSynonyms() {
        assertThat(taxonomy.canonicalName("  dubstep ")).isEqualTo("Dubstep");
        assertThat(taxonomy.canonicalName("pop")).isEqualTo("Pop");
        assertThat(taxonomy.canonicalName("agressive")).isEqualTo("Aggressive");
        assertThat(taxonomy.canonicalName("high energy")).isEqualTo("High Energy");
        assertThat(taxonomy.canonicalName("k-pop")).isEqualTo("K-Pop");
        assertThat(taxonomy.canonicalName("hip-hop")).isEqualTo("Hip Hop");
        assertThat(taxonomy.canonicalName("r&b")).isEqualTo("R&B");
        assertThat(taxonomy.canonicalName("female vocals")).isEqualTo("Female Vocals");
    }

    @Test
    void movesSubgenresAndMoodsOutOfTags() {
        Buckets buckets = taxonomy.classify(
                List.of("Electronic"),
                List.of(),
                List.of("dubstep", "aggressive", "female vocals"));

        assertThat(buckets.genres()).containsExactly("Electronic", "Dubstep");
        assertThat(buckets.moods()).containsExactly("Aggressive");
        assertThat(buckets.tags()).containsExactly("Female Vocals");
    }

    @Test
    void erasLeaveGenreAndMoodAdjectivesLeaveTags() {
        Buckets buckets = taxonomy.classify(
                List.of("Pop", "2010s"),
                List.of("Dreamy", "Sexy"),
                List.of("mysterious", "smooth", "female vocals"));

        assertThat(buckets.genres()).containsExactly("Pop");
        assertThat(buckets.moods()).containsExactly("Dreamy", "Sexy", "Mysterious", "Smooth");
        assertThat(buckets.tags()).containsExactly("2010s", "Female Vocals");
        assertThat(taxonomy.typeOf("2010s")).isEqualTo(TagType.TAGS);
        assertThat(taxonomy.typeOf("Pop")).isEqualTo(TagType.GENRE);
    }

    @Test
    void keepsUnknownMoodsThatWereAlreadyMoods() {
        Buckets buckets = taxonomy.classify(
                List.of("Pop"),
                List.of("Sports Arena"),
                List.of());

        assertThat(buckets.moods()).containsExactly("Sports Arena");
        assertThat(buckets.tags()).isEmpty();
    }
}
