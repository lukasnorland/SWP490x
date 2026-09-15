package com.funix.swp490x.mrs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import com.funix.swp490x.mrs.domain.RecommendationLog;
import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import com.funix.swp490x.mrs.llm.FilterMapper;
import com.funix.swp490x.mrs.llm.InterpretedFilters;
import com.funix.swp490x.mrs.llm.LlmInterpreter;
import com.funix.swp490x.mrs.repository.RecommendationLogRepository;
import com.funix.swp490x.mrs.repository.TagRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private LlmInterpreter interpreter;
    @Mock
    private FilterMapper filterMapper;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private SongCatalogService catalogService;
    @Mock
    private RecommendationLogRepository recommendationLogRepository;
    @Mock
    private SettingsService settings;

    private SearchService service;

    @BeforeEach
    void setUp() {
        lenient().when(settings.llmMinQueryChars()).thenReturn(10);
        lenient().when(settings.llmMaxQueryChars()).thenReturn(200);
        service = new SearchService(interpreter, settings, filterMapper, tagRepository,
                catalogService, recommendationLogRepository);
        lenient().when(tagRepository.findAllUsedOrderByTypeAscNameAsc()).thenReturn(List.of());
    }

    @Test
    void interpretRejectsShortQueries() {
        assertThatThrownBy(() -> service.interpretRedirect(1L, "too short", null))
                .isInstanceOf(InvalidSearchQueryException.class);
        then(recommendationLogRepository).should(never()).save(any());
    }

    @Test
    void interpretFallsBackToKeywordWhenNothingMatches() {
        given(interpreter.interpret(eq("upbeat summer campaign for a beach game"), any()))
                .willReturn(Optional.empty());
        given(catalogService.searchRecommended(eq(List.of()), eq(List.of()), eq(List.of()),
                eq(List.of()), eq("upbeat summer campaign for a beach game"), isNull(), eq(0)))
                .willReturn(new PageImpl<>(List.of(new Song())));

        SearchService.InterpretRedirect result =
                service.interpretRedirect(7L, "upbeat summer campaign for a beach game", null);

        assertThat(result.fallback()).isTrue();
        assertThat(result.path()).startsWith("/search?");
        assertThat(result.path()).contains("q=");
        ArgumentCaptor<RecommendationLog> captor = ArgumentCaptor.forClass(RecommendationLog.class);
        then(recommendationLogRepository).should().save(captor.capture());
        assertThat(captor.getValue().isLlmUsed()).isFalse();
        assertThat(captor.getValue().getLlmSucceeded()).isFalse();
        assertThat(captor.getValue().getResultCount()).isEqualTo(1);
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
    }

    @Test
    void interpretRedirectsToFilterIdsWhenVocabularyHits() {
        InterpretedFilters filters = new InterpretedFilters(List.of("Pop"), List.of("Energetic"),
                List.of(), List.of(), null);
        given(interpreter.interpret(eq("energetic pop playlist now"), any()))
                .willReturn(Optional.of(filters));
        given(filterMapper.map(filters)).willReturn(new FilterMapper.MappedFilters(
                List.of(2L), List.of(1L), List.of(), List.of()));
        given(catalogService.searchRecommended(eq(List.of(2L)), eq(List.of(1L)), eq(List.of()),
                eq(List.of()), isNull(), eq(10), eq(0)))
                .willReturn(Page.empty());

        SearchService.InterpretRedirect result =
                service.interpretRedirect(1L, "energetic pop playlist now", 10);

        assertThat(result.fallback()).isFalse();
        assertThat(result.path()).contains("genreId=2");
        assertThat(result.path()).contains("moodId=1");
        assertThat(result.path()).contains("topN=10");
        assertThat(result.path()).contains("prompt=");
        assertThat(result.path()).doesNotContain("q=");
        ArgumentCaptor<RecommendationLog> captor = ArgumentCaptor.forClass(RecommendationLog.class);
        then(recommendationLogRepository).should().save(captor.capture());
        assertThat(captor.getValue().getLlmSucceeded()).isNull();
        assertThat(captor.getValue().getInterpretedFilters()).contains("\"fallback\":false");
    }

    @Test
    void searchWithoutCriteriaIsEmpty() {
        assertThat(service.search(null, null, null, null, "  ", null, 0)).isEmpty();
        then(catalogService).shouldHaveNoInteractions();
    }

    @Test
    void interpretRedirect_whenQueryIsTenChars_shouldAccept() {
        String query = "a".repeat(10);
        given(interpreter.interpret(eq(query), any())).willReturn(Optional.empty());
        given(catalogService.searchRecommended(any(), any(), any(), any(), eq(query), isNull(),
                eq(0))).willReturn(Page.empty());

        assertThat(service.interpretRedirect(1L, query, null).path()).startsWith("/search?");
    }

    @Test
    void interpretRedirect_whenQueryIsTwoHundredChars_shouldAccept() {
        String query = "a".repeat(200);
        given(interpreter.interpret(eq(query), any())).willReturn(Optional.empty());
        given(catalogService.searchRecommended(any(), any(), any(), any(), eq(query), isNull(),
                eq(0))).willReturn(Page.empty());

        assertThat(service.interpretRedirect(1L, query, null).path()).startsWith("/search?");
    }

    @Test
    void interpretRedirect_whenQueryIsTwoHundredAndOne_shouldReject() {
        assertThatThrownBy(() -> service.interpretRedirect(1L, "a".repeat(201), null))
                .isInstanceOf(InvalidSearchQueryException.class);
        then(recommendationLogRepository).should(never()).save(any());
    }

    @Test
    void interpretRedirect_whenInterpreterThrows_shouldFallBackToKeyword() {
        String query = "upbeat summer campaign for a beach game";
        given(interpreter.interpret(eq(query), any())).willThrow(new RuntimeException("timeout"));
        given(catalogService.searchRecommended(eq(List.of()), eq(List.of()), eq(List.of()),
                eq(List.of()), eq(query), isNull(), eq(0)))
                .willReturn(new PageImpl<>(List.of(new Song())));

        SearchService.InterpretRedirect result = service.interpretRedirect(7L, query, null);

        assertThat(result.fallback()).isTrue();
        assertThat(result.path()).contains("q=");
        ArgumentCaptor<RecommendationLog> captor = ArgumentCaptor.forClass(RecommendationLog.class);
        then(recommendationLogRepository).should().save(captor.capture());
        assertThat(captor.getValue().getLlmSucceeded()).isFalse();
    }

    @Test
    void search_whenCriteriaGiven_shouldDelegateToSearchRecommended() {
        given(catalogService.searchRecommended(eq(List.of(2L)), eq(List.of(1L)), eq(List.of()),
                eq(List.of()), isNull(), isNull(), eq(0))).willReturn(Page.empty());

        service.search(List.of(2L), List.of(1L), List.of(), List.of(), "  ", null, 0);

        then(catalogService).should().searchRecommended(eq(List.of(2L)), eq(List.of(1L)),
                eq(List.of()), eq(List.of()), isNull(), isNull(), eq(0));
    }

    @Test
    void playQueue_shouldDelegateWithTheSameFiltersAndKeepOrder() {
        List<SongCatalogService.PreviewTrack> ranked = List.of(
                preview(11L), preview(10L));
        given(catalogService.playQueueRecommended(eq(List.of(2L)), eq(List.of(1L)), eq(List.of()),
                eq(List.of()), isNull(), eq(10))).willReturn(ranked);

        List<SongCatalogService.PreviewTrack> tracks =
                service.playQueue(List.of(2L), List.of(1L), List.of(), List.of(), "  ", 10);

        assertThat(tracks).extracting(SongCatalogService.PreviewTrack::id)
                .containsExactly(11L, 10L);
        assertThat(tracks).isEqualTo(ranked);
        then(catalogService).should().playQueueRecommended(eq(List.of(2L)), eq(List.of(1L)),
                eq(List.of()), eq(List.of()), isNull(), eq(10));
        then(catalogService).should(never()).playQueue(any(), any(), any(), any(), any(), any(),
                anyBoolean());
    }

    @Test
    void playQueue_whenNoCriteria_shouldReturnEmpty() {
        assertThat(service.playQueue(null, null, List.of(), null, "  ", null)).isEmpty();
        then(catalogService).shouldHaveNoInteractions();
    }

    @Test
    void resultSongIds_shouldCollectEveryPageInRankOrder() {
        List<Long> ranked = IntStream.rangeClosed(1, 3 * SongCatalogService.PAGE_SIZE)
                .mapToObj(i -> (long) i)
                .toList();
        given(catalogService.recommendedIds(eq(List.of(2L)), eq(List.of(1L)), eq(List.of()),
                eq(List.of()), isNull(), isNull())).willReturn(ranked);

        assertThat(service.resultSongIds(List.of(2L), List.of(1L), List.of(), List.of(), "  ",
                null)).containsExactlyElementsOf(ranked);
        then(catalogService).should().recommendedIds(eq(List.of(2L)), eq(List.of(1L)),
                eq(List.of()), eq(List.of()), isNull(), isNull());
        then(catalogService).should(never()).searchRecommended(any(), any(), any(), any(), any(),
                any(), anyInt());
    }

    @Test
    void resultSongIds_whenTopN_shouldStopAtN() {
        List<Long> capped = List.of(11L, 10L, 9L, 8L, 7L);
        given(catalogService.recommendedIds(eq(List.of(2L)), eq(List.of(1L)), eq(List.of()),
                eq(List.of()), isNull(), eq(5))).willReturn(capped);

        assertThat(service.resultSongIds(List.of(2L), List.of(1L), List.of(), List.of(), null, 5))
                .containsExactlyElementsOf(capped)
                .hasSize(5);
        then(catalogService).should().recommendedIds(eq(List.of(2L)), eq(List.of(1L)),
                eq(List.of()), eq(List.of()), isNull(), eq(5));
    }

    @Test
    void resultSongIds_whenNoMatch_shouldReturnEmpty() {
        given(catalogService.recommendedIds(eq(List.of(2L)), eq(List.of()), eq(List.of()),
                eq(List.of()), isNull(), isNull())).willReturn(List.of());

        assertThat(service.resultSongIds(List.of(2L), List.of(), List.of(), List.of(), null, null))
                .isEmpty();
        then(catalogService).should().recommendedIds(eq(List.of(2L)), eq(List.of()), eq(List.of()),
                eq(List.of()), isNull(), isNull());
    }

    @Test
    void resultSongIds_whenNoCriteria_shouldReturnEmpty() {
        assertThat(service.resultSongIds(null, null, List.of(), null, "  ", null)).isEmpty();
        then(catalogService).shouldHaveNoInteractions();
    }

    @Test
    void chips_whenNoFilters_shouldBeEmpty() {
        assertThat(service.chips(null, null, null, null, null, null, null)).isEmpty();
    }

    @Test
    void chipsOmitRemovedIdFromUrl() {
        Tag mood = new Tag(TagType.MOOD, "Energetic");
        ReflectionTestUtils.setField(mood, "id", 1L);
        given(tagRepository.findAllUsedOrderByTypeAscNameAsc()).willReturn(List.of(mood));

        var chips = service.chips(null, List.of(1L, 2L), null, null, null, "summer vibes", null);

        assertThat(chips).hasSize(2);
        assertThat(chips.get(0).removeUrl()).contains("moodId=2");
        assertThat(chips.get(0).removeUrl()).doesNotContain("moodId=1");
    }

    private static SongCatalogService.PreviewTrack preview(Long id) {
        return new SongCatalogService.PreviewTrack(id, "https://cdn.example/" + id + ".mp3",
                "Track " + id, "Artist", "https://cdn.example/cover.jpg", null, null, 180);
    }
}
