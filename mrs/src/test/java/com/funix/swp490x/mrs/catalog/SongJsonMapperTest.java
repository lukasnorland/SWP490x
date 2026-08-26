package com.funix.swp490x.mrs.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.funix.swp490x.mrs.catalog.SongJsonMapper.SongValues;
import com.funix.swp490x.mrs.catalog.SongJsonMapper.TagRef;
import com.funix.swp490x.mrs.domain.TagType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

/**
 * Maps the JSON the staging scripts actually produce, using real objects copied
 * out of staged catalog objects rather than hand-written samples.
 */
class SongJsonMapperTest {

    private static final List<String> REGISTERED = List.of("EpidemicSound", "DemoProvider");

    private final SongJsonMapper mapper = new SongJsonMapper();

    private static String fixture(String name) throws IOException {
        return new ClassPathResource("catalog/" + name).getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void mapsEveryFieldOfARealStagedObject() throws Exception {
        SongJsonMapper.Result result =
                mapper.map(fixture("003c5571-5014-387b-978c-2836125178a4.json"), REGISTERED);

        assertThat(result.isRejected()).isFalse();
        SongValues values = result.values();
        assertThat(values.externalSourceId()).isEqualTo("003c5571-5014-387b-978c-2836125178a4");
        assertThat(values.sourceProvider()).isEqualTo("EpidemicSound");
        assertThat(values.title()).isEqualTo("Ice Cream");
        assertThat(values.artist()).isEqualTo("Sugar Blizz");
        assertThat(values.duration()).isEqualTo(213);
        assertThat(values.bpm()).isEqualTo(110);
        assertThat(values.explicit()).isFalse();
        assertThat(values.isrc()).isEqualTo("SE5Q51900056");
        assertThat(values.audioUrl()).endsWith(".mp3");
        assertThat(values.coverUrl()).contains("cdn.epidemicsound.com");
    }

    /**
     * Genres, moods and freeform descriptors keep their own vocabulary, and the
     * artist is tagged as well so search can group by it (V1 tag types).
     */
    @Test
    void spreadsProviderMetadataAcrossTheTagTypes() throws Exception {
        SongJsonMapper.Result result =
                mapper.map(fixture("003c5571-5014-387b-978c-2836125178a4.json"), REGISTERED);

        assertThat(result.values().tags())
                .contains(new TagRef(TagType.GENRE, "Pop"),
                        new TagRef(TagType.MOOD, "Dreamy"),
                        new TagRef(TagType.TAGS, "smooth"),
                        new TagRef(TagType.ARTIST, "Sugar Blizz"));
    }

    /** DC-03 depends on this: an object with no metadata yields no tags. */
    @Test
    void anObjectWithoutMetadataYieldsNoTags() {
        String json = """
                {"externalSourceId":"x1","sourceProvider":"DemoProvider","title":"Bare"}
                """;

        assertThat(mapper.map(json, REGISTERED).values().tags()).isEmpty();
    }

    @Test
    void collapsesWhitespaceSoOneTagDoesNotBecomeTwo() {
        String json = """
                {"externalSourceId":"x1","sourceProvider":"DemoProvider","title":"T",
                 "moods":["  Laid   Back "," ","Laid Back"]}
                """;

        assertThat(mapper.map(json, REGISTERED).values().tags())
                .containsExactly(new TagRef(TagType.MOOD, "Laid Back"));
    }

    /** SC-05: a row missing what the catalog requires is reported, not written. */
    @Test
    void rejectsAnObjectWithNoTitle() {
        String json = """
                {"externalSourceId":"x1","sourceProvider":"DemoProvider","title":"  "}
                """;

        SongJsonMapper.Result result = mapper.map(json, REGISTERED);
        assertThat(result.isRejected()).isTrue();
        assertThat(result.rejection()).isEqualTo("missing title");
    }

    @Test
    void rejectsAnObjectWithNoExternalId() {
        String json = """
                {"sourceProvider":"DemoProvider","title":"Anonymous"}
                """;

        assertThat(mapper.map(json, REGISTERED).rejection())
                .isEqualTo("missing externalSourceId");
    }

    /** SC-05 exception: an unregistered source never reaches the catalog. */
    @Test
    void rejectsAnUnregisteredProvider() {
        String json = """
                {"externalSourceId":"x1","sourceProvider":"RandomLabel","title":"T"}
                """;

        assertThat(mapper.map(json, REGISTERED).rejection())
                .isEqualTo("unregistered provider 'RandomLabel'");
    }

    @Test
    void acceptsARegisteredProviderRegardlessOfCase() {
        String json = """
                {"externalSourceId":"x1","sourceProvider":"epidemicsound","title":"T"}
                """;

        assertThat(mapper.map(json, REGISTERED).isRejected()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not json at all", "{\"externalSourceId\":"})
    void rejectsRatherThanThrowsOnUnreadableContent(String json) {
        SongJsonMapper.Result result = mapper.map(json, REGISTERED);

        assertThat(result.isRejected()).isTrue();
        assertThat(result.rejection()).isNotBlank();
    }

    /** The columns are constrained to be positive, so nonsense is dropped. */
    @Test
    void dropsNonPositiveDurationAndBpmInsteadOfFailingTheRow() {
        String json = """
                {"externalSourceId":"x1","sourceProvider":"DemoProvider","title":"T",
                 "duration":0,"bpm":-4}
                """;

        SongValues values = mapper.map(json, REGISTERED).values();
        assertThat(values.duration()).isNull();
        assertThat(values.bpm()).isNull();
    }

    /** A provider field longer than its column must not fail the whole chunk. */
    @Test
    void truncatesOverlongValuesToFitTheirColumn() {
        String json = """
                {"externalSourceId":"x1","sourceProvider":"DemoProvider","title":"%s"}
                """.formatted("t".repeat(400));

        assertThat(mapper.map(json, REGISTERED).values().title()).hasSize(255);
    }

    @Test
    void ignoresProviderFieldsTheCatalogDoesNotKeep() {
        // Unknown upstream fields must not break an import.
        String json = """
                {"externalSourceId":"x1","sourceProvider":"DemoProvider","title":"T",
                 "epidemicTrackId":999,"publicSlug":"abc","energyLevel":"high","hasVocals":true}
                """;
        assertThat(mapper.map(json, REGISTERED).isRejected()).isFalse();
    }

    @Test
    void serialisesIsExplicitUnderTheCatalogKeyAndReadsItBack() {
        StagedSong staged = new StagedSong(
                "id-1", "EpidemicSound", "T", "A", 90, 120, true, "ISRC1",
                "https://cdn.example/a.mp3", "https://cdn.example/c.jpg",
                List.of("Pop"), List.of("Happy"), List.of("hook"));

        String json = mapper.write(staged);

        assertThat(json).contains("\"isExplicit\"");
        assertThat(json).doesNotContain("\"explicit\"");

        SongJsonMapper.Result result = mapper.map(json, REGISTERED);
        assertThat(result.isRejected()).isFalse();
        assertThat(result.values().explicit()).isTrue();
        assertThat(result.values().title()).isEqualTo("T");
        assertThat(result.values().audioUrl()).endsWith("a.mp3");
    }

    @Test
    void patchClassificationKeepsUnknownProviderFieldsAndLicensedIdentity() {
        String json = """
                {"externalSourceId":"x1","sourceProvider":"DemoProvider","title":"Ice Cream",
                 "artist":"Sugar Blizz","epidemicTrackId":999,"publicSlug":"abc",
                 "isExplicit":false,"genres":["Old"],"moods":["Whatever"],"tags":["stale"]}
                """;

        String patched = mapper.patchClassification(json, true, List.of("Pop"),
                List.of("Dreamy"), List.of("smooth"));

        assertThat(patched).contains("\"epidemicTrackId\" : 999");
        assertThat(patched).contains("\"publicSlug\" : \"abc\"");
        assertThat(patched).contains("\"title\" : \"Ice Cream\"");
        assertThat(patched).contains("\"artist\" : \"Sugar Blizz\"");
        assertThat(patched).contains("\"isExplicit\" : true");
        assertThat(patched).contains("\"Pop\"");
        assertThat(patched).doesNotContain("\"Old\"");
        assertThat(patched).doesNotContain("\"Whatever\"");
        assertThat(patched).doesNotContain("\"stale\"");
    }
}
