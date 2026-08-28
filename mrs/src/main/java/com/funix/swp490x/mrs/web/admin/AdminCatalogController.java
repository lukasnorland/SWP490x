package com.funix.swp490x.mrs.web.admin;

import com.funix.swp490x.mrs.catalog.CatalogImportService;
import com.funix.swp490x.mrs.catalog.CatalogImportService.ImportProgress;
import com.funix.swp490x.mrs.catalog.CatalogStoreException;
import com.funix.swp490x.mrs.catalog.ImportSummary;
import com.funix.swp490x.mrs.catalog.ImportSummary.SkippedRow;
import com.funix.swp490x.mrs.catalog.SongDraftUploadService;
import com.funix.swp490x.mrs.catalog.SongDraftUploadService.MediaUploadResult;
import com.funix.swp490x.mrs.domain.ImportTrigger;
import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.repository.TagRepository;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.service.PlaylistService;
import com.funix.swp490x.mrs.service.SongCatalogService;
import com.funix.swp490x.mrs.service.SongCatalogService.SongEdit;
import com.funix.swp490x.mrs.service.SongNotFoundException;
import com.funix.swp490x.mrs.service.StaleSongException;
import com.funix.swp490x.mrs.web.Messages;
import com.funix.swp490x.mrs.web.Routes;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * P-06b — Song Catalog & Metadata (spec 4.10, UC-29, UC-28).
 *
 * <p>Read is the Songs table. Update covers classification only (explicit,
 * genres, moods, tags) on both MySQL and the staged song-data JSON; licensed
 * identity is immutable. Delete runs from the same screen.
 *
 * <p>Create is Add Song: ADMIN drops audio + artwork + metadata, the server
 * writes song-data JSON and queues the ETag sync into MySQL. Sync Catalog
 * converts JSON already under the prefix (CLI dumps, previous uploads). The
 * provider CSV/XLSX upload of the original flow is still outstanding.
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
    private final CatalogImportService importService;
    private final SongDraftUploadService draftUploadService;
    private final PlaylistService playlistService;

    public AdminCatalogController(SongCatalogService catalogService,
            TagRepository tagRepository,
            CatalogImportService importService,
            SongDraftUploadService draftUploadService,
            PlaylistService playlistService) {
        this.catalogService = catalogService;
        this.tagRepository = tagRepository;
        this.importService = importService;
        this.draftUploadService = draftUploadService;
        this.playlistService = playlistService;
    }

    @GetMapping(Routes.ADMIN_CATALOG)
    public String catalog(@AuthenticationPrincipal MrsUserDetails actor,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Long genreId,
            @RequestParam(required = false) Long moodId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestHeader(value = PARTIAL_RESULTS_HEADER, required = false) String partial,
            Model model) {
        populateResults(model, provider, genreId, moodId, tagId, q, page);
        if (PARTIAL_RESULTS_VALUE.equals(partial)) {
            return "fragments/song-catalog :: results";
        }
        populateShell(model, actor);
        return "admin/catalog";
    }

    @PostMapping(Routes.ADMIN_CATALOG_SONG)
    public String save(@PathVariable Long id,
            @ModelAttribute SongEditForm form,
            @AuthenticationPrincipal MrsUserDetails actor,
            Model model,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {

        try {
            catalogService.update(id, form.getVersion(), toEdit(form));
        } catch (StaleSongException e) {
            return reject(model, response, HttpStatus.CONFLICT, Messages.SONG_STALE, false, form, id,
                    actor);
        } catch (SongNotFoundException e) {
            return reject(model, response, HttpStatus.NOT_FOUND, Messages.SONG_NOT_FOUND, false,
                    form, id, actor);
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

    @GetMapping(path = Routes.ADMIN_CATALOG_SYNC_STATUS, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ImportProgress syncStatus() {
        return importService.progress();
    }

    /**
     * Add Song. Without JavaScript this is an ordinary multipart POST that
     * redirects; the modal sends the same body over XHR so a large batch can
     * show upload progress, and reads the JSON reply for the rejection detail.
     */
    @PostMapping(Routes.ADMIN_CATALOG_SONGS)
    public Object uploadSongs(@ModelAttribute SongDraftBatchForm form,
            @AuthenticationPrincipal MrsUserDetails actor,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            RedirectAttributes redirectAttributes) {

        MediaUploadResult result = draftUploadService.upload(form.getDrafts(),
                actor == null ? null : actor.getId());
        boolean ajax = "XMLHttpRequest".equalsIgnoreCase(requestedWith);

        if (result.isEmptySelection()) {
            if (ajax) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", Messages.MEDIA_UPLOAD_EMPTY,
                        "rejected", List.of()));
            }
            flash(redirectAttributes, "warning", Messages.MEDIA_UPLOAD_EMPTY);
            return "redirect:" + Routes.ADMIN_CATALOG;
        }

        if (result.uploaded() == 0) {
            if (ajax) {
                return ResponseEntity.unprocessableEntity().body(Map.of(
                        "message", Messages.MEDIA_UPLOAD_ALL_REJECTED,
                        "rejected", result.rejected()));
            }
            flash(redirectAttributes, "danger",
                    withRejections(Messages.MEDIA_UPLOAD_ALL_REJECTED, result.rejected()));
            return "redirect:" + Routes.ADMIN_CATALOG;
        }

        ImportSummary sync = result.sync();
        String message;
        String variant;
        if (sync != null && sync.alreadyRunning()) {
            message = Messages.UPLOAD_SYNC_SKIPPED;
            variant = "warning";
        } else {
            message = "%d song(s) staged. Import started — this page will show progress."
                    .formatted(result.uploaded());
            variant = result.rejected().isEmpty() ? "success" : "warning";
        }
        message = withRejections(message, result.rejected());

        if (ajax) {
            return ResponseEntity.ok(Map.of(
                    "uploaded", result.uploaded(),
                    "message", message,
                    "redirect", Routes.ADMIN_CATALOG));
        }

        flash(redirectAttributes, variant, message);
        return "redirect:" + Routes.ADMIN_CATALOG;
    }

    @PostMapping(Routes.ADMIN_CATALOG_SYNC)
    public String sync(@AuthenticationPrincipal MrsUserDetails actor,
            RedirectAttributes redirectAttributes) {

        boolean started = importService.startAsync(ImportTrigger.MANUAL,
                actor == null ? null : actor.getId(), false);

        if (started) {
            flash(redirectAttributes, "info", Messages.IMPORT_STARTED);
        } else {
            flash(redirectAttributes, "warning", Messages.IMPORT_ALREADY_RUNNING);
        }

        return "redirect:" + Routes.ADMIN_CATALOG;
    }

    /**
     * FT-09 NAC-03: a rejected song is named with its reason rather than
     * silently dropped. The catalog has no room for a rejection table, so the
     * reasons ride along in the notice.
     */
    private static String withRejections(String message, List<SkippedRow> rejected) {
        if (rejected.isEmpty()) {
            return message;
        }
        return message + " " + rejected.stream()
                .map(row -> "%s — %s".formatted(row.key(), row.reason()))
                .collect(Collectors.joining("; "));
    }

    private void populateResults(Model model, String provider, Long genreId, Long moodId,
            Long tagId, String q, int page) {
        Page<Song> songs = catalogService.search(provider, genreId, moodId, tagId, q, page);
        model.addAttribute("songs", songs);
        model.addAttribute("catalogBasePath", Routes.ADMIN_CATALOG);
        model.addAttribute("browseMode", false);
        // Echoed back so the filter form and the pager keep the current query.
        model.addAttribute("filterProvider", provider);
        model.addAttribute("filterGenreId", genreId);
        model.addAttribute("filterMoodId", moodId);
        model.addAttribute("filterTagId", tagId);
        model.addAttribute("filterQuery", q == null ? "" : q);
    }

    /**
     * Only on a full-page GET: the Add-to-playlist dialog sits outside
     * {@code #catalog-results}, so the partial response has no use for the list
     * and should not pay for the query.
     */
    private void populateShell(Model model, MrsUserDetails actor) {
        List<Tag> tags = tagRepository.findAllUsedOrderByTypeAscNameAsc();
        model.addAttribute("myPlaylists", actor == null
                ? List.of()
                : playlistService.editableDrafts(actor.getId()));
        model.addAttribute("pageTitle", "Song Catalog");
        model.addAttribute("activeNav", "admin-catalog");
        model.addAttribute("totalSongs", catalogService.total());
        model.addAttribute("untaggedCount", catalogService.untaggedCount());
        // Filters list names currently on a song, not leftover dictionary rows.
        // Add Song lists the vendors the upload service knows how to key for.
        model.addAttribute("providers", catalogService.providers());
        model.addAttribute("uploadProviders", draftUploadService.registeredProviders());
        model.addAttribute("tags", tags);
        model.addAttribute("importRunning", importService.isRunning());
        model.addAttribute("catalogSource", importService.sourceDescription());
        importService.lastRun().ifPresent(run -> model.addAttribute("lastRun", run));
    }

    /**
     * A stale save (BR-06) re-renders with current rows and does not reopen
     * the modal — refresh only, no clone. Validation failures reopen it.
     */
    private String reject(Model model, HttpServletResponse response, HttpStatus status,
            String message, boolean reopenModal, SongEditForm form, Long id,
            MrsUserDetails actor) {
        response.setStatus(status.value());
        model.addAttribute("flash", message);
        model.addAttribute("flashVariant", "danger");
        model.addAttribute("reopenEditForm", reopenModal);
        model.addAttribute("editSongId", id);
        model.addAttribute("songEdit", form);
        populateResults(model, null, null, null, null, null, 0);
        populateShell(model, actor);
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
