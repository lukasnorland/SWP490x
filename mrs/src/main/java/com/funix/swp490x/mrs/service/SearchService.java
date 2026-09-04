package com.funix.swp490x.mrs.service;

import com.funix.swp490x.mrs.catalog.CatalogTaxonomy;
import com.funix.swp490x.mrs.domain.RecommendationLog;
import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.llm.FilterMapper;
import com.funix.swp490x.mrs.llm.FilterMapper.MappedFilters;
import com.funix.swp490x.mrs.llm.FilterVocabulary;
import com.funix.swp490x.mrs.llm.InterpretedFilters;
import com.funix.swp490x.mrs.llm.GeminiLlmInterpreter;
import com.funix.swp490x.mrs.llm.LlmInterpreter;
import com.funix.swp490x.mrs.llm.LlmProperties;
import com.funix.swp490x.mrs.repository.RecommendationLogRepository;
import com.funix.swp490x.mrs.repository.TagRepository;
import com.funix.swp490x.mrs.web.Routes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * P-02 Search: interpret a playlist-need prompt, keep every song that matches
 * at least one chip, rank by how many chips hit, and record
 * {@code recommendation_log}.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final LlmInterpreter interpreter;
    private final LlmProperties properties;
    private final FilterMapper filterMapper;
    private final TagRepository tagRepository;
    private final CatalogTaxonomy taxonomy = new CatalogTaxonomy();
    private final SongCatalogService catalogService;
    private final RecommendationLogRepository recommendationLogRepository;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public SearchService(LlmInterpreter interpreter, LlmProperties properties,
            FilterMapper filterMapper, TagRepository tagRepository,
            SongCatalogService catalogService,
            RecommendationLogRepository recommendationLogRepository) {
        this.interpreter = interpreter;
        this.properties = properties;
        this.filterMapper = filterMapper;
        this.tagRepository = tagRepository;
        this.catalogService = catalogService;
        this.recommendationLogRepository = recommendationLogRepository;
    }

    /**
     * Interpret once, log, return a GET {@code /search?...} so paging does not
     * call the interpreter again.
     */
    @Transactional
    public String interpretRedirect(Long userId, String rawQuery, Integer topN) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        int min = properties.getMinQueryChars();
        int max = properties.getMaxQueryChars();
        if (query.length() < min || query.length() > max) {
            throw new InvalidSearchQueryException(
                    "Describe the playlist in " + min + "–" + max + " characters.");
        }

        FilterVocabulary vocabulary = FilterVocabulary.of(
                tagRepository.findAllUsedOrderByTypeAscNameAsc(), taxonomy);
        Optional<InterpretedFilters> interpreted = Optional.empty();
        try {
            interpreted = interpreter.interpret(query, vocabulary);
        } catch (RuntimeException e) {
            log.warn("Search interpretation failed; using keyword fallback", e);
        }

        boolean fallback = interpreted.isEmpty() || interpreted.get().isEmpty();
        MappedFilters mapped = fallback
                ? MappedFilters.empty()
                : filterMapper.map(interpreted.get());
        if (!fallback && mapped.isEmpty()) {
            fallback = true;
        }

        String keyword = fallback ? query : null;
        Page<Song> results = catalogService.searchRecommended(
                mapped.genreIds(), mapped.moodIds(), mapped.artistIds(), mapped.tagIds(),
                keyword, topN, 0);

        boolean llmUsed = interpreter instanceof GeminiLlmInterpreter;
        Boolean llmSucceeded = fallback
                ? Boolean.FALSE
                : (llmUsed ? Boolean.TRUE : null);

        recommendationLogRepository.save(new RecommendationLog(
                userId,
                query,
                detailsJson(interpreted.orElse(InterpretedFilters.empty()), mapped, fallback),
                llmUsed,
                llmSucceeded,
                (int) results.getTotalElements()));

        return redirectPath(mapped, keyword, topN, query);
    }

    @Transactional(readOnly = true)
    public Page<Song> search(List<Long> genreIds, List<Long> moodIds, List<Long> artistIds,
            List<Long> tagIds, String query, Integer topN, int page) {

        if (empty(genreIds) && empty(moodIds) && empty(artistIds) && empty(tagIds)
                && !StringUtils.hasText(query)) {
            return new PageImpl<>(List.of(), PageRequest.of(Math.max(page, 0),
                    SongCatalogService.PAGE_SIZE), 0);
        }
        return catalogService.searchRecommended(genreIds, moodIds, artistIds, tagIds,
                StringUtils.hasText(query) ? query.trim() : null, topN, page);
    }

    @Transactional(readOnly = true)
    public List<SongCatalogService.PreviewTrack> playQueue(List<Long> genreIds,
            List<Long> moodIds, List<Long> artistIds, List<Long> tagIds, String query,
            Integer topN) {

        if (empty(genreIds) && empty(moodIds) && empty(artistIds) && empty(tagIds)
                && !StringUtils.hasText(query)) {
            return List.of();
        }
        return catalogService.playQueueRecommended(genreIds, moodIds, artistIds, tagIds,
                StringUtils.hasText(query) ? query.trim() : null, topN);
    }

    /**
     * Every song id the current filters match, across all pages, so "Create
     * playlist from results" lands the same set the curator is looking at.
     */
    @Transactional(readOnly = true)
    public List<Long> resultSongIds(List<Long> genreIds, List<Long> moodIds,
            List<Long> artistIds, List<Long> tagIds, String query, Integer topN) {

        if (empty(genreIds) && empty(moodIds) && empty(artistIds) && empty(tagIds)
                && !StringUtils.hasText(query)) {
            return List.of();
        }
        return catalogService.recommendedIds(genreIds, moodIds, artistIds, tagIds,
                StringUtils.hasText(query) ? query.trim() : null, topN);
    }

    private String detailsJson(InterpretedFilters interpreted, MappedFilters mapped,
            boolean fallback) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("genres", interpreted.genres());
        body.put("moods", interpreted.moods());
        body.put("artists", interpreted.artists());
        body.put("tags", interpreted.tags());
        body.put("confidence", interpreted.confidence());
        body.put("fallback", fallback);
        body.put("genreIds", mapped.genreIds());
        body.put("moodIds", mapped.moodIds());
        body.put("artistIds", mapped.artistIds());
        body.put("tagIds", mapped.tagIds());
        try {
            return jsonMapper.writeValueAsString(body);
        } catch (RuntimeException e) {
            return "{\"fallback\":" + fallback + "}";
        }
    }

    static String redirectPath(MappedFilters mapped, String keyword, Integer topN, String prompt) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(Routes.SEARCH);
        appendIds(builder, "genreId", mapped.genreIds());
        appendIds(builder, "moodId", mapped.moodIds());
        appendIds(builder, "artistId", mapped.artistIds());
        appendIds(builder, "tagId", mapped.tagIds());
        if (StringUtils.hasText(keyword)) {
            builder.queryParam("q", keyword);
        }
        if (StringUtils.hasText(prompt)) {
            builder.queryParam("prompt", prompt);
        }
        if (topN != null && topN > 0) {
            builder.queryParam("topN", topN);
        }
        return builder.build().encode().toUriString();
    }

    private static void appendIds(UriComponentsBuilder builder, String name, List<Long> ids) {
        if (ids == null) {
            return;
        }
        for (Long id : ids) {
            if (id != null) {
                builder.queryParam(name, id);
            }
        }
    }

    private static boolean empty(List<Long> values) {
        return values == null || values.isEmpty();
    }

    /** Chip shown on P-02 Zone C; {@code removeUrl} drops this id from the query. */
    public record SearchChip(String type, Long id, String label, String removeUrl) {
    }

    public List<SearchChip> chips(List<Long> genreIds, List<Long> moodIds, List<Long> artistIds,
            List<Long> tagIds, String keyword, String prompt, Integer topN) {

        List<SearchChip> chips = new ArrayList<>();
        Map<Long, String> names = new LinkedHashMap<>();
        tagRepository.findAllUsedOrderByTypeAscNameAsc()
                .forEach(tag -> names.put(tag.getId(), tag.getName()));
        addChips(chips, "Genre", genreIds, names, genreIds, moodIds, artistIds, tagIds, keyword,
                prompt, topN);
        addChips(chips, "Mood", moodIds, names, genreIds, moodIds, artistIds, tagIds, keyword,
                prompt, topN);
        addChips(chips, "Artist", artistIds, names, genreIds, moodIds, artistIds, tagIds, keyword,
                prompt, topN);
        addChips(chips, "Tag", tagIds, names, genreIds, moodIds, artistIds, tagIds, keyword,
                prompt, topN);
        return List.copyOf(chips);
    }

    private void addChips(List<SearchChip> chips, String type, List<Long> ofType,
            Map<Long, String> names, List<Long> genreIds, List<Long> moodIds,
            List<Long> artistIds, List<Long> tagIds, String keyword, String prompt,
            Integer topN) {
        if (ofType == null) {
            return;
        }
        for (Long id : ofType) {
            if (id == null) {
                continue;
            }
            String label = names.getOrDefault(id, "#" + id);
            String remove = redirectPath(
                    without(genreIds, moodIds, artistIds, tagIds, type, id),
                    keyword, topN, prompt);
            chips.add(new SearchChip(type, id, label, remove));
        }
    }

    private static MappedFilters without(List<Long> genreIds, List<Long> moodIds,
            List<Long> artistIds, List<Long> tagIds, String type, Long id) {
        return new MappedFilters(
                "Genre".equals(type) ? minus(genreIds, id) : orEmpty(genreIds),
                "Mood".equals(type) ? minus(moodIds, id) : orEmpty(moodIds),
                "Artist".equals(type) ? minus(artistIds, id) : orEmpty(artistIds),
                "Tag".equals(type) ? minus(tagIds, id) : orEmpty(tagIds));
    }

    private static List<Long> minus(List<Long> values, Long id) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(value -> !id.equals(value)).toList();
    }

    private static List<Long> orEmpty(List<Long> values) {
        return values == null ? List.of() : values;
    }
}
