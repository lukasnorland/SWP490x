package com.funix.swp490x.mrs.web.admin;

import com.funix.swp490x.mrs.catalog.CatalogStoreException;
import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.repository.TagRepository;
import com.funix.swp490x.mrs.service.SongCatalogService;
import com.funix.swp490x.mrs.service.SongCatalogService.SongEdit;
import com.funix.swp490x.mrs.service.SongNotFoundException;
import com.funix.swp490x.mrs.service.StaleSongException;
import com.funix.swp490x.mrs.web.Messages;
import com.funix.swp490x.mrs.web.Routes;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * P-06b — Song Catalog & Metadata (spec 4.10, UC-29).
 *
 * <p>Read is the Songs table. Update covers classification only (explicit,
 * genres, moods, tags) on both MySQL and the staged song-data JSON; licensed
 * identity is immutable. Delete runs from the same screen. Create stays on
 * Catalog Import (P-06c). The Tags dictionary is still outstanding.
 *
 * <p>Full-page GETs render the shell. Requests with {@code X-MRS-Partial: results}
 * return only the table + pager fragment so the player bar stays mounted.
 */
@Controller
public class AdminCatalogController {

    public static final String PARTIAL_RESULTS_HEADER = "X-MRS-Partial";
    public static final String PARTIAL_RESULTS_VALUE = "results";

    private static final Logger log = LoggerFactory.getLogger(AdminCatalogController.class);

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
            return "fragments/song-catalog :: results";
        }
        populateShell(model);
        return "admin/catalog";
    }

    @PostMapping(Routes.ADMIN_CATALOG_SONG)
    public String save(@PathVariable Long id,
            @ModelAttribute SongEditForm form,
            Model model,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {

        try {
            catalogService.update(id, form.getVersion(), toEdit(form));
        } catch (StaleSongException e) {
            return reject(model, response, HttpStatus.CONFLICT, Messages.SONG_STALE, false, form, id);
        } catch (SongNotFoundException e) {
            return reject(model, response, HttpStatus.NOT_FOUND, Messages.SONG_NOT_FOUND, false,
                    form, id);
        } catch (CatalogStoreException e) {
            log.error("Could not write staged JSON for song {}", id, e);
            flash(redirectAttributes, "danger", Messages.SONG_SAVE_FAILED);
            return "redirect:" + Routes.ADMIN_CATALOG;
        }

        flash(redirectAttributes, "success", Messages.SONG_SAVED);
        return "redirect:" + Routes.ADMIN_CATALOG;
    }

    @PostMapping(Routes.ADMIN_CATALOG_SONG_DELETE)
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            catalogService.delete(id);
            flash(redirectAttributes, "success", Messages.SONG_DELETED);
        } catch (SongNotFoundException e) {
            flash(redirectAttributes, "danger", Messages.SONG_NOT_FOUND);
        } catch (CatalogStoreException e) {
            log.error("Could not delete staged JSON for song {}", id, e);
            flash(redirectAttributes, "danger", Messages.SONG_DELETE_FAILED);
        }
        return "redirect:" + Routes.ADMIN_CATALOG;
    }

    private void populateResults(Model model, String provider, Long tagId, String q,
            boolean untagged, boolean noPreview, int page) {
        Page<Song> songs = catalogService.search(provider, tagId, q, untagged, noPreview, page);
        model.addAttribute("songs", songs);
        model.addAttribute("catalogBasePath", Routes.ADMIN_CATALOG);
        model.addAttribute("browseMode", false);
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

    /**
     * A stale save (BR-06) re-renders with current rows and does not reopen
     * the modal — refresh only, no clone. Validation failures reopen it.
     */
    private String reject(Model model, HttpServletResponse response, HttpStatus status,
            String message, boolean reopenModal, SongEditForm form, Long id) {
        response.setStatus(status.value());
        model.addAttribute("flash", message);
        model.addAttribute("flashVariant", "danger");
        model.addAttribute("reopenEditForm", reopenModal);
        model.addAttribute("editSongId", id);
        model.addAttribute("songEdit", form);
        populateResults(model, null, null, null, false, false, 0);
        populateShell(model);
        return "admin/catalog";
    }

    private static SongEdit toEdit(SongEditForm form) {
        return new SongEdit(
                Boolean.TRUE.equals(form.getExplicit()),
                form.getGenres(),
                form.getMoods(),
                form.getTags());
    }

    private void flash(RedirectAttributes redirectAttributes, String variant, String message) {
        redirectAttributes.addFlashAttribute("flash", message);
        redirectAttributes.addFlashAttribute("flashVariant", variant);
    }
}
