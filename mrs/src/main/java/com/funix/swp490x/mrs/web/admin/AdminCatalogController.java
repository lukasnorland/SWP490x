package com.funix.swp490x.mrs.web.admin;

import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.repository.TagRepository;
import com.funix.swp490x.mrs.service.SongCatalogService;
import com.funix.swp490x.mrs.web.Routes;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * P-06b — Song Catalog & Metadata (spec 4.10).
 *
 * <p>Read-only for now: the catalog is filled by the import on P-06c, so the
 * screen's job is to show what landed and which songs are still untagged.
 * Editing a song (UC-29) and the Tags tab are still outstanding.
 *
 * <p>Full-page GETs render the shell. Requests with {@code X-MRS-Partial: results}
 * return only the table + pager fragment so the player bar stays mounted.
 */
@Controller
public class AdminCatalogController {

    public static final String PARTIAL_RESULTS_HEADER = "X-MRS-Partial";
    public static final String PARTIAL_RESULTS_VALUE = "results";

    private final SongCatalogService catalogService;
    private final TagRepository tagRepository;

    public AdminCatalogController(SongCatalogService catalogService,
            TagRepository tagRepository) {
        this.catalogService = catalogService;
        this.tagRepository = tagRepository;
    }

    @GetMapping(Routes.ADMIN_CATALOG)
    public String catalog(@RequestParam(required = false) String provider,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean untagged,
            @RequestParam(defaultValue = "false") boolean noPreview,
            @RequestParam(defaultValue = "0") int page,
            @RequestHeader(value = PARTIAL_RESULTS_HEADER, required = false) String partial,
            Model model) {
        populateResults(model, provider, tagId, q, untagged, noPreview, page);
        if (PARTIAL_RESULTS_VALUE.equals(partial)) {
            return "admin/catalog :: results";
        }
        populateShell(model);
        return "admin/catalog";
    }

    private void populateResults(Model model, String provider, Long tagId, String q,
            boolean untagged, boolean noPreview, int page) {
        Page<Song> songs = catalogService.search(provider, tagId, q, untagged, noPreview, page);
        model.addAttribute("songs", songs);
        // Echoed back so the filter form and the pager keep the current query.
        model.addAttribute("filterProvider", provider);
        model.addAttribute("filterTagId", tagId);
        model.addAttribute("filterQuery", q == null ? "" : q);
        model.addAttribute("filterUntagged", untagged);
        model.addAttribute("filterNoPreview", noPreview);
    }

    private void populateShell(Model model) {
        List<Tag> tags = tagRepository.findAllByOrderByTypeAscNameAsc();
        model.addAttribute("pageTitle", "Song Catalog & Metadata");
        model.addAttribute("activeNav", "admin-catalog");
        model.addAttribute("totalSongs", catalogService.total());
        model.addAttribute("untaggedCount", catalogService.untaggedCount());
        model.addAttribute("providers", catalogService.providers());
        model.addAttribute("tags", tags);
    }
}
