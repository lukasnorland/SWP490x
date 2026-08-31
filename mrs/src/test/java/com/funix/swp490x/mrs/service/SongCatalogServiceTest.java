package com.funix.swp490x.mrs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import com.funix.swp490x.mrs.catalog.CatalogObjectStore;
import com.funix.swp490x.mrs.catalog.CatalogProperties;
import com.funix.swp490x.mrs.catalog.CatalogStoreException;
import com.funix.swp490x.mrs.catalog.InvalidClassificationException;
import com.funix.swp490x.mrs.catalog.SongJsonMapper;
import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import com.funix.swp490x.mrs.repository.SongRepository;
import com.funix.swp490x.mrs.repository.TagRepository;
import com.funix.swp490x.mrs.service.SongCatalogService.SongEdit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SongCatalogServiceTest {

    @Mock
    private SongRepository songRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private CatalogObjectStore catalogStore;

    private SongCatalogService service;

    @BeforeEach
    void setUp() {
        service = new SongCatalogService(songRepository, tagRepository, catalogStore,
                new CatalogProperties(), new SongJsonMapper());
    }

    @Test
    void searchORsWithinAVocabularyAndSkipsEmptyLists() {
        given(songRepository.searchIds(eq(true), eq(List.of("")), eq(false), eq(List.of(3L, 7L)),
                eq(true), eq(List.of(-1L)), eq(true), eq(List.of(-1L)), eq(true), eq(List.of(-1L)),
                eq(null), any()))
                .willReturn(Page.empty());

        Page<Song> result = service.search(null, List.of(3L, 7L), null, null, "  ", 0);

        assertThat(result.isEmpty()).isTrue();
        then(songRepository).should().searchIds(eq(true), eq(List.of("")), eq(false),
                eq(List.of(3L, 7L)), eq(true), eq(List.of(-1L)), eq(true), eq(List.of(-1L)),
                eq(true), eq(List.of(-1L)), eq(null), any());
    }

    @Test
    void searchBindsSelectedProviders() {
        given(songRepository.searchIds(eq(false), eq(List.of("EpidemicSound", "NCS")),
                eq(true), eq(List.of(-1L)), eq(true), eq(List.of(-1L)), eq(true), eq(List.of(-1L)),
                eq(true), eq(List.of(-1L)), eq("ice"), any()))
                .willReturn(Page.empty());

        service.search(List.of("EpidemicSound", "NCS"), null, List.of(), null, "ice", 0);

        then(songRepository).should().searchIds(eq(false), eq(List.of("EpidemicSound", "NCS")),
                eq(true), eq(List.of(-1L)), eq(true), eq(List.of(-1L)), eq(true), eq(List.of(-1L)),
                eq(true), eq(List.of(-1L)), eq("ice"), any());
    }

    @Test
    void playQueueKeepsTableOrderAndDropsSongsWithNoAudio() {
        given(songRepository.searchIds(eq(true), eq(List.of("")), eq(false), eq(List.of(3L, 7L)),
                eq(true), eq(List.of(-1L)), eq(true), eq(List.of(-1L)), eq(true), eq(List.of(-1L)),
                eq(null), any()))
                .willReturn(new PageImpl<>(List.of(3L, 1L, 2L)));
        Song silent = existing(1L, 0);
        silent.setTitle("Muted");
        Song first = playable(3L, "Alpha", "https://cdn.example/a.mp3");
        Song second = playable(2L, "Zulu", "https://cdn.example/z.mp3");
        given(songRepository.findAllById(List.of(3L, 1L, 2L)))
                .willReturn(List.of(silent, second, first));

        List<SongCatalogService.PreviewTrack> tracks =
                service.playQueue(null, List.of(3L, 7L), null, null, "  ");

        assertThat(tracks).extracting(SongCatalogService.PreviewTrack::id)
                .containsExactly(3L, 2L);
        assertThat(tracks.get(0).url()).isEqualTo("https://cdn.example/a.mp3");
        assertThat(tracks.get(0).title()).isEqualTo("Alpha");
        assertThat(tracks.get(0).artist()).isEqualTo("Sugar Blizz");
        assertThat(tracks.get(0).cover()).isEqualTo("https://cdn.example/cover.jpg");
        assertThat(tracks.get(0).ambienceA()).isEqualTo("rgba(1, 2, 3, 0.4)");
        assertThat(tracks.get(0).ambienceB()).isEqualTo("rgba(4, 5, 6, 0.4)");
        assertThat(tracks.get(0).duration()).isEqualTo(180);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        then(songRepository).should().searchIds(eq(true), eq(List.of("")), eq(false),
                eq(List.of(3L, 7L)), eq(true), eq(List.of(-1L)), eq(true), eq(List.of(-1L)),
                eq(true), eq(List.of(-1L)), eq(null), pageable.capture());
        assertThat(pageable.getValue().isUnpaged()).isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("title").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(pageable.getValue().getSort().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        then(songRepository).should().findAllById(List.of(3L, 1L, 2L));
    }

    @Test
    void playQueueIsEmptyWhenNothingMatches() {
        given(songRepository.searchIds(eq(true), eq(List.of("")), eq(true), eq(List.of(-1L)),
                eq(true), eq(List.of(-1L)), eq(true), eq(List.of(-1L)), eq(true), eq(List.of(-1L)),
                eq(null), any()))
                .willReturn(Page.empty());

        assertThat(service.playQueue(null, null, null, null, null)).isEmpty();
        then(songRepository).should(never()).findAllById(any());
    }

    @Test
    void searchBindsArtistIds() {
        given(songRepository.searchIds(eq(true), eq(List.of("")), eq(true), eq(List.of(-1L)),
                eq(true), eq(List.of(-1L)), eq(false), eq(List.of(9L)), eq(true), eq(List.of(-1L)),
                eq(null), any()))
                .willReturn(Page.empty());

        service.search(null, null, null, List.of(9L), null, null, 0);

        then(songRepository).should().searchIds(eq(true), eq(List.of("")), eq(true),
                eq(List.of(-1L)), eq(true), eq(List.of(-1L)), eq(false), eq(List.of(9L)),
                eq(true), eq(List.of(-1L)), eq(null), any());
    }

    @Test
    void searchRecommendedOrdersByMatchedChipCountThenCapsTopN() {
        given(songRepository.searchIdsMatchingAny(eq(true), eq(List.of("")), eq(false),
                eq(List.of(1L)), eq(false), eq(List.of(2L)), eq(true), eq(List.of(-1L)),
                eq(true), eq(List.of(-1L)), eq(null), any()))
                .willReturn(new PageImpl<>(List.of(10L, 11L, 12L)));
        Song oneHit = tagged(10L, "Alpha", 1L);
        Song twoHits = tagged(11L, "Zulu", 1L, 2L);
        Song alsoTwo = tagged(12L, "Beta", 1L, 2L);
        given(songRepository.findAllWithTags(List.of(10L, 11L, 12L)))
                .willReturn(List.of(oneHit, twoHits, alsoTwo));

        Page<Song> page = service.searchRecommended(List.of(1L), List.of(2L), null, null, null, 2, 0);

        assertThat(page.getContent()).extracting(Song::getId).containsExactly(12L, 11L);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void searchRecommendedUnionsVocabulariesInsteadOfRequiringEveryChip() {
        given(songRepository.searchIdsMatchingAny(eq(true), eq(List.of("")), eq(false),
                eq(List.of(1L)), eq(false), eq(List.of(2L)), eq(true), eq(List.of(-1L)),
                eq(true), eq(List.of(-1L)), eq(null), any()))
                .willReturn(new PageImpl<>(List.of(10L, 20L)));
        Song popOnly = tagged(10L, "Alpha", 1L);
        Song moodOnly = tagged(20L, "Zulu", 2L);
        given(songRepository.findAllWithTags(List.of(10L, 20L)))
                .willReturn(List.of(popOnly, moodOnly));

        Page<Song> page = service.searchRecommended(List.of(1L), List.of(2L), null, null, null,
                null, 0);

        assertThat(page.getContent()).extracting(Song::getId).containsExactly(10L, 20L);
        then(songRepository).should(never()).searchIds(eq(true), eq(List.of("")), eq(false),
                eq(List.of(1L)), eq(false), eq(List.of(2L)), eq(true), eq(List.of(-1L)),
                eq(true), eq(List.of(-1L)), eq(null), any());
    }

    @Test
    void updateWritesClassificationAndLeavesLicensedIdentityAlone() {
        Song song = existing(12L, 3);
        song.setTitle("Ice Cream");
        song.setArtist("Sugar Blizz");
        song.setDuration(213);
        song.setBpm(110);
        song.setIsrc("SE5Q51900056");
        song.setExternalSourceId("abc-123");
        given(songRepository.findByIdWithTags(12L)).willReturn(Optional.of(song));
        given(tagRepository.findByTypeAndName(any(), any())).willReturn(Optional.empty());
        given(tagRepository.save(any(Tag.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(songRepository.save(song)).willReturn(song);
        given(catalogStore.stagingKey("abc-123")).willReturn("abc-123.json");
        given(catalogStore.findJson("abc-123.json")).willReturn(Optional.of("""
                {"externalSourceId":"abc-123","sourceProvider":"NCS","title":"Ice Cream",
                 "artist":"Sugar Blizz","epidemicTrackId":999,"isExplicit":false,
                 "genres":["Old"],"moods":["Whatever"],"tags":["stale"]}
                """));
        given(catalogStore.putJson(eq("abc-123.json"), anyString())).willReturn("new-etag");

        service.update(12L, 3, new SongEdit(true, "Pop", "Dreamy", "smooth"));

        assertThat(song.getTitle()).isEqualTo("Ice Cream");
        assertThat(song.getArtist()).isEqualTo("Sugar Blizz");
        assertThat(song.getDuration()).isEqualTo(213);
        assertThat(song.getBpm()).isEqualTo(110);
        assertThat(song.getIsrc()).isEqualTo("SE5Q51900056");
        assertThat(song.getExplicit()).isTrue();
        assertThat(song.getSourceEtag()).isEqualTo("new-etag");
        assertThat(song.getTags())
                .extracting(Tag::getName)
                .containsExactlyInAnyOrder("Pop", "Dreamy", "Smooth", "Sugar Blizz");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(catalogStore, songRepository);
        order.verify(catalogStore).putJson(eq("abc-123.json"), body.capture());
        order.verify(songRepository).save(song);
        assertThat(body.getValue()).contains("\"isExplicit\" : true");
        assertThat(body.getValue()).contains("\"Pop\"");
        assertThat(body.getValue()).contains("\"Dreamy\"");
        assertThat(body.getValue()).contains("\"Smooth\"");
        assertThat(body.getValue()).contains("\"epidemicTrackId\" : 999");
        assertThat(body.getValue()).contains("\"title\" : \"Ice Cream\"");
        assertThat(body.getValue()).doesNotContain("\"Old\"");
    }

    @Test
    void updateWritesStagedJsonFromTheRowWhenTheObjectIsMissing() {
        Song song = existing(12L, 3);
        song.setExternalSourceId("abc-123");
        song.setAudioUrl("https://cdn.example/a.mp3");
        given(songRepository.findByIdWithTags(12L)).willReturn(Optional.of(song));
        given(tagRepository.findByTypeAndName(any(), any())).willReturn(Optional.empty());
        given(tagRepository.save(any(Tag.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(songRepository.save(song)).willReturn(song);
        given(catalogStore.stagingKey("abc-123")).willReturn("abc-123.json");
        given(catalogStore.findJson("abc-123.json")).willReturn(Optional.empty());
        given(catalogStore.putJson(eq("abc-123.json"), anyString())).willReturn("etag-1");

        service.update(12L, 3, new SongEdit(false, "Pop", null, null));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        then(catalogStore).should().putJson(eq("abc-123.json"), body.capture());
        assertThat(body.getValue()).contains("\"externalSourceId\" : \"abc-123\"");
        assertThat(body.getValue()).contains("\"title\" : \"Was\"");
        assertThat(body.getValue()).contains("\"Pop\"");
        assertThat(song.getSourceEtag()).isEqualTo("etag-1");
    }

    @Test
    void updateRefusesWhenTheSongHasNoExternalId() {
        Song song = existing(12L, 3);
        given(songRepository.findByIdWithTags(12L)).willReturn(Optional.of(song));

        assertThatThrownBy(() -> service.update(12L, 3, new SongEdit(false, null, null, null)))
                .isInstanceOf(CatalogStoreException.class)
                .hasMessageContaining("externalSourceId");

        then(catalogStore).shouldHaveNoInteractions();
        then(songRepository).should(never()).save(any());
    }

    @Test
    void updateDoesNotSaveWhenStagedJsonCannotBeWritten() {
        Song song = existing(12L, 3);
        song.setExternalSourceId("abc-123");
        given(songRepository.findByIdWithTags(12L)).willReturn(Optional.of(song));
        given(catalogStore.stagingKey("abc-123")).willReturn("abc-123.json");
        given(catalogStore.findJson("abc-123.json")).willReturn(Optional.empty());
        given(catalogStore.putJson(eq("abc-123.json"), anyString()))
                .willThrow(new CatalogStoreException("denied"));

        assertThatThrownBy(() -> service.update(12L, 3, new SongEdit(false, "Pop", null, null)))
                .isInstanceOf(CatalogStoreException.class);

        then(songRepository).should(never()).save(any());
    }

    @Test
    void updateRejectsAStaleVersionWithoutWriting() {
        Song song = existing(12L, 4);
        given(songRepository.findByIdWithTags(12L)).willReturn(Optional.of(song));

        assertThatThrownBy(() -> service.update(12L, 3, new SongEdit(false, null, null, null)))
                .isInstanceOf(StaleSongException.class);

        then(songRepository).should(never()).save(any());
        then(catalogStore).shouldHaveNoInteractions();
    }

    @Test
    void updateRejectsUnknownGenresBeforeWriting() {
        Song song = existing(12L, 3);
        song.setExternalSourceId("abc-123");
        given(songRepository.findByIdWithTags(12L)).willReturn(Optional.of(song));

        assertThatThrownBy(() -> service.update(12L, 3, new SongEdit(false, "Cinematic", null, null)))
                .isInstanceOf(InvalidClassificationException.class)
                .hasMessageContaining("Cinematic");

        then(catalogStore).shouldHaveNoInteractions();
        then(songRepository).should(never()).save(any());
    }

    @Test
    void deleteRemovesHostedMediaThenStagedJsonThenTheRow() {
        Song song = existing(12L, 0);
        song.setExternalSourceId("abc-123");
        song.setAudioUrl("https://d34ixswlpjs53y.cloudfront.net/song-data/audio/ncs/abc-123.mp3");
        song.setCoverUrl("https://cdn.epidemicsound.com/cover.jpg");
        given(songRepository.findById(12L)).willReturn(Optional.of(song));
        given(catalogStore.stagingKey("abc-123")).willReturn("abc-123.json");
        given(catalogStore.findJson("abc-123.json")).willReturn(Optional.empty());

        service.delete(12L);

        InOrder order = inOrder(catalogStore, songRepository);
        order.verify(catalogStore).deleteBinary("song-data/audio/ncs/abc-123.mp3");
        order.verify(catalogStore).deleteJson("abc-123.json");
        order.verify(songRepository).detachFromPlaylists(List.of(12L));
        order.verify(songRepository).delete(song);
        then(catalogStore).should(never()).deleteBinary(eq("song-data/artwork/ncs/abc-123.jpg"));
    }

    @Test
    void deleteAlsoRemovesHostedMediaNamedOnlyInStagedJson() {
        Song song = existing(12L, 0);
        song.setExternalSourceId("abc-123");
        given(songRepository.findById(12L)).willReturn(Optional.of(song));
        given(catalogStore.stagingKey("abc-123")).willReturn("abc-123.json");
        given(catalogStore.findJson("abc-123.json")).willReturn(Optional.of("""
                {"externalSourceId":"abc-123","sourceProvider":"NCS","title":"Was",
                 "audioUrl":"https://d34ixswlpjs53y.cloudfront.net/song-data/audio/ncs/abc-123.mp3",
                 "coverUrl":"https://d34ixswlpjs53y.cloudfront.net/song-data/artwork/ncs/abc-123.jpg"}
                """));

        service.delete(12L);

        then(catalogStore).should().deleteBinary("song-data/audio/ncs/abc-123.mp3");
        then(catalogStore).should().deleteBinary("song-data/artwork/ncs/abc-123.jpg");
        then(catalogStore).should().deleteJson("abc-123.json");
    }

    @Test
    void deleteDoesNotTouchTheRowWhenStagedJsonCannotBeRemoved() {
        Song song = existing(12L, 0);
        song.setExternalSourceId("abc-123");
        given(songRepository.findById(12L)).willReturn(Optional.of(song));
        given(catalogStore.stagingKey("abc-123")).willReturn("abc-123.json");
        given(catalogStore.findJson("abc-123.json")).willReturn(Optional.empty());
        willThrow(new CatalogStoreException("denied")).given(catalogStore).deleteJson("abc-123.json");

        assertThatThrownBy(() -> service.delete(12L))
                .isInstanceOf(CatalogStoreException.class);

        then(songRepository).should(never()).detachFromPlaylists(any());
        then(songRepository).should(never()).delete(any(Song.class));
    }

    @Test
    void deleteSkipsStagingWhenTheSongHasNoExternalId() {
        Song song = existing(12L, 0);
        given(songRepository.findById(12L)).willReturn(Optional.of(song));

        service.delete(12L);

        then(catalogStore).shouldHaveNoInteractions();
        then(songRepository).should().detachFromPlaylists(List.of(12L));
        then(songRepository).should().delete(song);
    }

    @Test
    void deleteRejectsAnUnknownId() {
        given(songRepository.findById(12L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(12L))
                .isInstanceOf(SongNotFoundException.class);
    }

    private static Song playable(Long id, String title, String audioUrl) {
        Song song = existing(id, 0);
        song.setTitle(title);
        song.setArtist("Sugar Blizz");
        song.setAudioUrl(audioUrl);
        song.setCoverUrl("https://cdn.example/cover.jpg");
        song.setAmbienceA("rgba(1, 2, 3, 0.4)");
        song.setAmbienceB("rgba(4, 5, 6, 0.4)");
        song.setDuration(180);
        return song;
    }

    private static Song tagged(Long id, String title, Long... tagIds) {
        Song song = existing(id, 0);
        song.setTitle(title);
        for (Long tagId : tagIds) {
            Tag tag = new Tag(TagType.GENRE, "t" + tagId);
            ReflectionTestUtils.setField(tag, "id", tagId);
            song.getTags().add(tag);
        }
        return song;
    }

    private static Song existing(Long id, int version) {
        Song song = new Song();
        song.setId(id);
        song.setTitle("Was");
        song.setSourceProvider("NCS");
        ReflectionTestUtils.setField(song, "version", version);
        return song;
    }
}
