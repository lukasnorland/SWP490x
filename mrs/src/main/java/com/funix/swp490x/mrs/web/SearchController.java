package com.funix.swp490x.mrs.web;

import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.service.InvalidSearchQueryException;
import com.funix.swp490x.mrs.service.PlaylistService;
import com.funix.swp490x.mrs.service.SearchService;
import com.funix.swp490x.mrs.service.SongCatalogService.PreviewTrack;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** P-02 Search & Recommendation (FT-03, FT-04, FT-05). */
@Controller
public class SearchController {

    public static final String PARTIAL_RESULTS_HEADER = "X-MRS-Partial";
    public static final String PARTIAL_RESULTS_VALUE = "results";

    private final SearchService searchService;
    private final PlaylistService playlistService;

    public SearchController(SearchService searchService, PlaylistService playlistService) {
        this.searchService = searchService;
        this.playlistService = playlistService;
    }

    @GetMapping(Routes.SEARCH)
    public String search(@AuthenticationPrincipal MrsUserDetails user,
            @RequestParam(required = false) List<Long> genreId,
            @RequestParam(required = false) List<Long> moodId,
            @RequestParam(required = false) List<Long> artistId,
            @RequestParam(required = false) List<Long> tagId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String prompt,
            @RequestParam(required = false) Integer topN,
            @RequestParam(defaultValue = "0") int page,
            @RequestHeader(value = PARTIAL_RESULTS_HEADER, required = false) String partial,
            Model model) {

        populateResults(model, genreId, moodId, artistId, tagId, q, prompt, topN, page);
        if (PARTIAL_RESULTS_VALUE.equals(partial)) {
            return "fragments/search-results :: results";
        }
        populateShell(model, user);
        return "search/index";
    }

    @PostMapping(Routes.SEARCH_INTERPRET)
    public String interpret(@AuthenticationPrincipal MrsUserDetails user,
            @RequestParam String q,
            @RequestParam(required = false) Integer topN,
            RedirectAttributes redirectAttributes) {
        try {
            return "redirect:" + searchService.interpretRedirect(userId(user), q, topN);
        } catch (InvalidSearchQueryException e) {
            redirectAttributes.addFlashAttribute("flash", Messages.SEARCH_QUERY_LENGTH);
            redirectAttributes.addFlashAttribute("flashVariant", "warning");
            redirectAttributes.addFlashAttribute("promptQuery", q);
            return "redirect:" + Routes.SEARCH;
        }
    }

    @GetMapping(path = Routes.SEARCH_PLAY_QUEUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, List<PreviewTrack>> playQueue(
            @RequestParam(required = false) List<Long> genreId,
            @RequestParam(required = false) List<Long> moodId,
            @RequestParam(required = false) List<Long> artistId,
            @RequestParam(required = false) List<Long> tagId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer topN) {
        return Map.of("tracks",
                searchService.playQueue(genreId, moodId, artistId, tagId, q, topN));
    }

    private void populateResults(Model model, List<Long> genreIds, List<Long> moodIds,
            List<Long> artistIds, List<Long> tagIds, String q, String prompt, Integer topN,
            int page) {

        Page<Song> songs = searchService.search(genreIds, moodIds, artistIds, tagIds, q, topN,
                page);
        String keyword = q == null ? "" : q.trim();
        String promptText = prompt == null ? "" : prompt.trim();
        if (promptText.isEmpty() && !keyword.isEmpty()) {
            promptText = keyword;
        }
        model.addAttribute("songs", songs);
        model.addAttribute("catalogBasePath", Routes.SEARCH);
        model.addAttribute("browseMode", true);
        model.addAttribute("filterProviders", List.of());
        model.addAttribute("filterGenreIds", orEmpty(genreIds));
        model.addAttribute("filterMoodIds", orEmpty(moodIds));
        model.addAttribute("filterArtistIds", orEmpty(artistIds));
        model.addAttribute("filterTagIds", orEmpty(tagIds));
        model.addAttribute("filterQuery", keyword);
        model.addAttribute("filterPrompt", promptText);
        model.addAttribute("filterTopN", topN == null || topN < 1 ? "" : String.valueOf(topN));
        model.addAttribute("promptQuery", promptText);
        List<Long> active = new java.util.ArrayList<>();
        active.addAll(orEmpty(genreIds));
        active.addAll(orEmpty(moodIds));
        active.addAll(orEmpty(artistIds));
        active.addAll(orEmpty(tagIds));
        model.addAttribute("activeFilterIds", active);
        model.addAttribute("chips", searchService.chips(genreIds, moodIds, artistIds, tagIds,
                keyword.isEmpty() ? null : keyword, promptText.isEmpty() ? null : promptText, topN));
        model.addAttribute("hasSearchCriteria", !active.isEmpty() || !keyword.isEmpty());
    }

    private void populateShell(Model model, MrsUserDetails user) {
        model.addAttribute("pageTitle", "Search & Recommendation");
        model.addAttribute("activeNav", "search");
        model.addAttribute("myPlaylists", user == null
                ? List.of()
                : playlistService.editableDrafts(user.getId()));
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null || values.isEmpty() ? List.of() : values;
    }

    private static Long userId(MrsUserDetails user) {
        return user == null ? null : user.getId();
    }
}
