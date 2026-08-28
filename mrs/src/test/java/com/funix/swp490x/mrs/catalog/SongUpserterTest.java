package com.funix.swp490x.mrs.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.funix.swp490x.mrs.catalog.CatalogImportService.Fetched;
import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import com.funix.swp490x.mrs.repository.SongRepository;
import com.funix.swp490x.mrs.repository.TagRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;

/** How one chunk decides between insert, update and refusal. */
class SongUpserterTest {

    private final Map<String, Tag> tags = new HashMap<>();
    private SongRepository songRepository;
    private TagRepository tagRepository;
    private SongUpserter upserter;

    @BeforeEach
    void setUp() {
        songRepository = mock(SongRepository.class);
        given(songRepository.save(any(Song.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        tagRepository = mock(TagRepository.class);
        given(tagRepository.findByTypeAndName(any(), any())).willAnswer(invocation ->
                Optional.ofNullable(tags.get(
                        invocation.getArgument(0) + ":" + invocation.getArgument(1))));
        given(tagRepository.save(any(Tag.class))).willAnswer(invocation -> {
            Tag tag = invocation.getArgument(0);
            tags.put(tag.getType() + ":" + tag.getName(), tag);
            return tag;
        });

        upserter = new SongUpserter(songRepository, tagRepository, new SongJsonMapper(),
                new CatalogProperties());
    }

    @Test
    void writesTheContentHashWithTheRowItDescribes() {
        upserter.upsertChunk(List.of(fetched("a", "etag-a", song("a", "Song A"))));

        verify(songRepository).save(any(Song.class));
        assertThat(savedSong().getSourceEtag()).isEqualTo("etag-a");
    }

    /** DC-04: the same external id updates in place rather than duplicating. */
    @Test
    void countsAKnownExternalIdAsAnUpdate() {
        Song existing = new Song();
        existing.setSourceProvider("NCS");
        existing.setExternalSourceId("a");
        given(songRepository.findBySourceProviderAndExternalSourceId("NCS", "a"))
                .willReturn(Optional.of(existing));

        SongUpserter.ChunkResult result =
                upserter.upsertChunk(List.of(fetched("a", "etag-a", song("a", "Renamed"))));

        assertThat(result.added()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        assertThat(existing.getTitle()).isEqualTo("Renamed");
    }

    /** Shared vocabulary: a genre common to two songs must not double up. */
    @Test
    void createsEachTagOncePerChunk() {
        upserter.upsertChunk(List.of(
                fetched("a", "e1", songWithGenre("a", "Pop")),
                fetched("b", "e2", songWithGenre("b", "Pop"))));

        verify(tagRepository, times(1)).save(any(Tag.class));
        assertThat(tags).containsOnlyKeys(TagType.GENRE + ":Pop");
    }

    @Test
    void reusesATagThatAlreadyExists() {
        tags.put(TagType.GENRE + ":Pop", new Tag(TagType.GENRE, "Pop"));

        upserter.upsertChunk(List.of(fetched("a", "e1", songWithGenre("a", "Pop"))));

        verify(tagRepository, never()).save(any(Tag.class));
    }

    /**
     * BR-06: a concurrent edit wins. The row is reported as skipped and keeps its
     * old ETag, so the next sync offers the object again.
     */
    @Test
    void reportsAVersionConflictInsteadOfOverwritingTheEdit() {
        given(songRepository.save(any(Song.class)))
                .willThrow(new OptimisticLockingFailureException("stale"));

        SongUpserter.ChunkResult result =
                upserter.upsertChunk(List.of(fetched("a", "etag-a", song("a", "Song A"))));

        assertThat(result.added()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.skipped()).singleElement().satisfies(row ->
                assertThat(row.reason()).contains("version conflict"));
    }

    @Test
    void oneBadObjectDoesNotStopTheRestOfTheChunk() {
        SongUpserter.ChunkResult result = upserter.upsertChunk(List.of(
                fetched("bad", "e1", "{not json"),
                fetched("good", "e2", song("good", "Fine"))));

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.skipped()).singleElement().satisfies(row ->
                assertThat(row.key()).isEqualTo("bad"));
    }

    @Test
    void skipsARowWhoseIsrcAlreadyBelongsToAnotherSong() {
        Song owner = new Song();
        owner.setSourceProvider("EpidemicSound");
        owner.setExternalSourceId("uuid");
        given(songRepository.findByIsrc("SE5Q51900056")).willReturn(Optional.of(owner));

        SongUpserter.ChunkResult result = upserter.upsertChunk(List.of(fetched("clip.json", "e1",
                """
                        {"externalSourceId":"clip","sourceProvider":"OneOff",
                         "title":"T","isrc":"SE5Q51900056"}
                        """)));

        assertThat(result.added()).isZero();
        assertThat(result.skipped()).singleElement().satisfies(row ->
                assertThat(row.reason()).contains("duplicate ISRC"));
        verify(songRepository, never()).save(any(Song.class));
    }

    @Test
    void aSongMayKeepItsOwnIsrcOnUpdate() {
        Song owner = new Song();
        owner.setSourceProvider("OneOff");
        owner.setExternalSourceId("clip");
        given(songRepository.findByIsrc("SE5Q51900056")).willReturn(Optional.of(owner));
        given(songRepository.findBySourceProviderAndExternalSourceId("OneOff", "clip"))
                .willReturn(Optional.of(owner));

        SongUpserter.ChunkResult result = upserter.upsertChunk(List.of(fetched("clip.json", "e2",
                """
                        {"externalSourceId":"clip","sourceProvider":"OneOff",
                         "title":"T","isrc":"SE5Q51900056"}
                        """)));

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.skipped()).isEmpty();
    }

    @Test
    void doesNotRollBackARowAlreadyMovedByALaterCatalogWrite() {
        Song existing = new Song();
        existing.setSourceProvider("NCS");
        existing.setExternalSourceId("a");
        existing.setTitle("Edited");
        existing.setSourceEtag("etag-b");
        given(songRepository.findBySourceProviderAndExternalSourceId("NCS", "a"))
                .willReturn(Optional.of(existing));

        SongUpserter.ChunkResult result = upserter.upsertChunk(List.of(
                fetched("a", "etag-a", song("a", "Stale fetch"), "etag-x")));

        assertThat(result.updated()).isZero();
        assertThat(result.skipped()).singleElement().satisfies(row ->
                assertThat(row.reason()).contains("later catalog write"));
        assertThat(existing.getTitle()).isEqualTo("Edited");
        assertThat(existing.getSourceEtag()).isEqualTo("etag-b");
        verify(songRepository, never()).save(any(Song.class));
    }

    @Test
    void stillAppliesWhenMysqlStillHasTheEtagTheListingCompared() {
        Song existing = new Song();
        existing.setSourceProvider("NCS");
        existing.setExternalSourceId("a");
        existing.setTitle("Was");
        existing.setSourceEtag("etag-x");
        given(songRepository.findBySourceProviderAndExternalSourceId("NCS", "a"))
                .willReturn(Optional.of(existing));

        SongUpserter.ChunkResult result = upserter.upsertChunk(List.of(
                fetched("a", "etag-a", song("a", "From S3"), "etag-x")));

        assertThat(result.updated()).isEqualTo(1);
        assertThat(existing.getTitle()).isEqualTo("From S3");
        assertThat(existing.getSourceEtag()).isEqualTo("etag-a");
    }

    @Test
    void pruneUnusedTagsDeletesDictionaryRowsNoSongStillCarries() {
        given(tagRepository.deleteUnused()).willReturn(4);

        assertThat(upserter.pruneUnusedTags()).isEqualTo(4);
        verify(tagRepository).deleteUnused();
    }

    private Song savedSong() {
        ArgumentCaptor<Song> captor = ArgumentCaptor.forClass(Song.class);
        verify(songRepository).save(captor.capture());
        return captor.getValue();
    }

    private static Fetched fetched(String key, String etag, String json) {
        return fetched(key, etag, json, null);
    }

    private static Fetched fetched(String key, String etag, String json, String listedSourceEtag) {
        return new Fetched(new CatalogObject(key, etag), json, listedSourceEtag);
    }

    private static String song(String id, String title) {
        return """
                {"externalSourceId":"%s","sourceProvider":"NCS","title":"%s"}
                """.formatted(id, title);
    }

    private static String songWithGenre(String id, String genre) {
        return """
                {"externalSourceId":"%s","sourceProvider":"NCS","title":"T",
                 "genres":["%s"]}
                """.formatted(id, genre);
    }
}
