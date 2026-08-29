package com.funix.swp490x.mrs.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(taxonomy.canonicalName("nu-disco")).isEqualTo("Nu Disco");
        assertThat(taxonomy.canonicalName("brazilian funk")).isEqualTo("Funk Carioca");
        assertThat(taxonomy.canonicalName("bossa")).isEqualTo("Bossa Nova");
        assertThat(taxonomy.canonicalName("Bossa Nova Nova Beat")).isEqualTo("Bossa Nova Beat");
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
    void unknownMoodsAndNonMusicBrainzGenresBecomeTags() {
        Buckets buckets = taxonomy.classify(
                List.of("Pop", "Cinematic", "Acoustic"),
                List.of("Sports Arena"),
                List.of());

        assertThat(buckets.genres()).containsExactly("Pop");
        assertThat(buckets.moods()).isEmpty();
        assertThat(buckets.tags()).containsExactly("Cinematic", "Acoustic", "Sports Arena");
    }

    @Test
    void mapsProviderAliasesOntoMusicBrainzNames() {
        Buckets buckets = taxonomy.classify(
                List.of("Rap", "Indie", "Hardcore", "Alternative"),
                List.of(),
                List.of());

        assertThat(buckets.genres()).containsExactly(
                "Hip Hop", "Indie Rock", "Hardcore Techno", "Alternative Rock");
    }

    @Test
    void requireAllowlistedRejectsUnknownGenresAndMoods() {
        assertThatThrownBy(() -> taxonomy.requireAllowlisted(List.of("Cinematic"), List.of("Dreamy")))
                .isInstanceOf(InvalidClassificationException.class)
                .hasMessageContaining("Cinematic");
        taxonomy.requireAllowlisted(List.of("Pop"), List.of("Dreamy"));
    }

    @Test
    void suggestPrefersPrefixMatches() {
        List<String> names = taxonomy.suggest(TagType.GENRE, "dub", 20);
        assertThat(names.getFirst()).startsWith("Dub");
        assertThat(taxonomy.allGenres()).hasSizeGreaterThan(2000);
        assertThat(taxonomy.suggest(TagType.MOOD, "en", 10)).contains("Energetic");
    }
}
