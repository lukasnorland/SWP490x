package com.funix.swp490x.mrs.web.admin;

import com.funix.swp490x.mrs.catalog.CatalogImportService;
import com.funix.swp490x.mrs.catalog.CatalogImportService.ImportProgress;
import com.funix.swp490x.mrs.catalog.ImportSummary;
import com.funix.swp490x.mrs.catalog.SongDraftUploadService;
import com.funix.swp490x.mrs.catalog.SongDraftUploadService.MediaUploadResult;
import com.funix.swp490x.mrs.domain.ImportTrigger;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.web.Messages;
import com.funix.swp490x.mrs.web.Routes;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * P-06c — Catalog Import (spec 4.11, UC-28, flow F-05).
 *
 * <p>ADMIN drops audio + artwork + metadata; the server writes song-data JSON
 * and queues the ETag sync into MySQL. Run import converts JSON already under
 * the prefix (CLI dumps, previous uploads). The provider CSV/XLSX upload of
 * the original flow is still outstanding.
 */
@Controller
public class AdminImportController {

    private final CatalogImportService importService;
    private final SongDraftUploadService draftUploadService;

    public AdminImportController(CatalogImportService importService,
            SongDraftUploadService draftUploadService) {
        this.importService = importService;
        this.draftUploadService = draftUploadService;
    }

    @GetMapping(Routes.ADMIN_IMPORT)
    public String importCatalog(Model model) {
        model.addAttribute("pageTitle", "Catalog Import");
        model.addAttribute("activeNav", "admin-import");
        model.addAttribute("importRunning", importService.isRunning());
        model.addAttribute("providers", draftUploadService.registeredProviders());
        model.addAttribute("catalogSource", importService.sourceDescription());
        importService.lastRun().ifPresent(run -> model.addAttribute("lastRun", run));
        return "admin/import";
    }

    @GetMapping(path = Routes.ADMIN_IMPORT_STATUS, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ImportProgress status() {
        return importService.progress();
    }

    @PostMapping(Routes.ADMIN_IMPORT_MEDIA)
    public Object uploadMedia(@ModelAttribute SongDraftBatchForm form,
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
            return "redirect:" + Routes.ADMIN_IMPORT;
        }

        if (result.uploaded() == 0) {
            if (ajax) {
                return ResponseEntity.unprocessableEntity().body(Map.of(
                        "message", Messages.MEDIA_UPLOAD_ALL_REJECTED,
                        "rejected", result.rejected()));
            }
            flash(redirectAttributes, "danger", Messages.MEDIA_UPLOAD_ALL_REJECTED);
            redirectAttributes.addFlashAttribute("mediaRejected", result.rejected());
            return "redirect:" + Routes.ADMIN_IMPORT;
        }

        if (!result.rejected().isEmpty()) {
            redirectAttributes.addFlashAttribute("mediaRejected", result.rejected());
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

        if (ajax) {
            return ResponseEntity.ok(Map.of(
                    "uploaded", result.uploaded(),
                    "message", message,
                    "redirect", Routes.ADMIN_IMPORT));
        }

        flash(redirectAttributes, variant, message);
        return "redirect:" + Routes.ADMIN_IMPORT;
    }

    @PostMapping(Routes.ADMIN_IMPORT_RUN)
    public String run(@RequestParam(defaultValue = "false") boolean force,
            @AuthenticationPrincipal MrsUserDetails actor,
            RedirectAttributes redirectAttributes) {

        boolean started = importService.startAsync(ImportTrigger.MANUAL,
                actor == null ? null : actor.getId(), force);

        if (started) {
            flash(redirectAttributes, "info", Messages.IMPORT_STARTED);
        } else {
            flash(redirectAttributes, "warning", Messages.IMPORT_ALREADY_RUNNING);
        }

        return "redirect:" + Routes.ADMIN_IMPORT;
    }

    private void flash(RedirectAttributes redirectAttributes, String variant, String message) {
        redirectAttributes.addFlashAttribute("flash", message);
        redirectAttributes.addFlashAttribute("flashVariant", variant);
    }
}
