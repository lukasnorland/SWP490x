package com.funix.swp490x.mrs.web;

import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.repository.TagRepository;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.service.PlaylistService;
import com.funix.swp490x.mrs.service.SongCatalogService;
import com.funix.swp490x.mrs.service.SongCatalogService.PreviewTrack;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Song browse for Content Designer. Reuses {@link SongCatalogService} and the
 * same preview-player wiring as P-06b, without admin import or metadata ops.
 * ADMIN is sent to {@link Routes#ADMIN_CATALOG}; Customers are closed out.
 *
 * <p>Full-page GETs render the shell. Requests with {@code X-MRS-Partial: results}
 * return only the table + pager fragment so the player bar stays mounted.
 */
@Controller
public class SongBrowseController {

    public static final String PARTIAL_RESULTS_HEADER = "X-MRS-Partial";
    public static final String PARTIAL_RESULTS_VALUE = "results";

    private final SongCatalogService catalogService;
    private final TagRepository tagRepository;
    private final PlaylistService playlistService;

    public SongBrowseController(SongCatalogService catalogService,
            TagRepository tagRepository,
            PlaylistService playlistService) {
        this.catalogService = catalogService;
        this.tagRepository = tagRepository;
        this.playlistService = playlistService;
    }

    @GetMapping(Routes.SONGS)
    public String songs(@AuthenticationPrincipal MrsUserDetails user,
            @RequestParam(required = false) List<String> provider,
            @RequestParam(required = false) List<Long> genreId,
            @RequestParam(required = false) List<Long> moodId,
            @RequestParam(required = false) List<Long> tagId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestHeader(value = PARTIAL_RESULTS_HEADER, required = false) String partial,
            Model model) {
        if (user != null && user.isAdmin()) {
            return "redirect:" + Routes.ADMIN_CATALOG;
        }
        populateResults(model, provider, genreId, moodId, tagId, q, page);
        if (PARTIAL_RESULTS_VALUE.equals(partial)) {
            return "fragments/song-catalog :: results";
        }
        populateShell(model, user);
        return "songs/index";
    }

    /**
     * Full filtered catalog for the preview bar. ADMIN is not redirected: the
     * admin catalog page uses this same URL so next/previous can leave the
     * current page of 20.
     */
    @GetMapping(path = Routes.SONGS_PLAY_QUEUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, List<PreviewTrack>> playQueue(
            @RequestParam(required = false) List<String> provider,
            @RequestParam(required = false) List<Long> genreId,
            @RequestParam(required = false) List<Long> moodId,
            @RequestParam(required = false) List<Long> tagId,
            @RequestParam(required = false) String q) {
        return Map.of("tracks",
                catalogService.playQueue(provider, genreId, moodId, tagId, q));
    }

    private void populateResults(Model model, List<String> providers, List<Long> genreIds,
            List<Long> moodIds, List<Long> tagIds, String q, int page) {
        Page<Song> songs = catalogService.search(providers, genreIds, moodIds, tagIds, q, page);
        model.addAttribute("songs", songs);
        model.addAttribute("catalogBasePath", Routes.SONGS);
        model.addAttribute("browseMode", true);
        model.addAttribute("filterProviders", orEmpty(providers));
        model.addAttribute("filterGenreIds", orEmpty(genreIds));
        model.addAttribute("filterMoodIds", orEmpty(moodIds));
        model.addAttribute("filterTagIds", orEmpty(tagIds));
        model.addAttribute("filterArtistIds", List.of());
        model.addAttribute("filterQuery", q == null ? "" : q);
        model.addAttribute("filterTopN", "");
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null || values.isEmpty() ? List.of() : values;
    }

    /**
     * Only on a full-page GET: the Add-to-playlist dialog sits outside
     * {@code #catalog-results}, so the partial response has no use for the list
     * and should not pay for the query.
     */
    private void populateShell(Model model, MrsUserDetails user) {
        List<Tag> tags = tagRepository.findAllUsedOrderByTypeAscNameAsc();
        model.addAttribute("pageTitle", "Songs");
        model.addAttribute("activeNav", "songs");
        model.addAttribute("totalSongs", catalogService.total());
        model.addAttribute("providers", catalogService.providers());
        model.addAttribute("tags", tags);
        model.addAttribute("myPlaylists", user == null
                ? List.of()
                : playlistService.editableDrafts(user.getId()));
    }
}
