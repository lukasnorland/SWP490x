package com.funix.swp490x.mrs.web;

import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.repository.TagRepository;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.service.SongCatalogService;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

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

    public SongBrowseController(SongCatalogService catalogService,
            TagRepository tagRepository) {
        this.catalogService = catalogService;
        this.tagRepository = tagRepository;
    }

    @GetMapping(Routes.SONGS)
    public String songs(@AuthenticationPrincipal MrsUserDetails user,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Long genreId,
            @RequestParam(required = false) Long moodId,
            @RequestParam(required = false) Long tagId,
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
        populateShell(model);
        return "songs/index";
    }

    private void populateResults(Model model, String provider, Long genreId, Long moodId,
            Long tagId, String q, int page) {
        Page<Song> songs = catalogService.search(provider, genreId, moodId, tagId, q, page);
        model.addAttribute("songs", songs);
        model.addAttribute("catalogBasePath", Routes.SONGS);
        model.addAttribute("browseMode", true);
        model.addAttribute("filterProvider", provider);
        model.addAttribute("filterGenreId", genreId);
        model.addAttribute("filterMoodId", moodId);
        model.addAttribute("filterTagId", tagId);
        model.addAttribute("filterQuery", q == null ? "" : q);
    }

    private void populateShell(Model model) {
        List<Tag> tags = tagRepository.findAllByOrderByTypeAscNameAsc();
        model.addAttribute("pageTitle", "Songs");
        model.addAttribute("activeNav", "songs");
        model.addAttribute("totalSongs", catalogService.total());
        model.addAttribute("providers", catalogService.providers());
        model.addAttribute("tags", tags);
    }
}
