package com.funix.swp490x.mrs.web.admin;

import com.funix.swp490x.mrs.catalog.CatalogImportService;
import com.funix.swp490x.mrs.catalog.CatalogImportService.PendingChanges;
import com.funix.swp490x.mrs.catalog.CatalogStoreException;
import com.funix.swp490x.mrs.catalog.CatalogUploadService;
import com.funix.swp490x.mrs.catalog.CatalogUploadService.UploadResult;
import com.funix.swp490x.mrs.catalog.ImportSummary;
import com.funix.swp490x.mrs.domain.ImportTrigger;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.web.Messages;
import com.funix.swp490x.mrs.web.Routes;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * P-06c — Catalog Import (spec 4.11, UC-28, flow F-05).
 *
 * <p>ADMIN can stage song JSON through the upload form (validated, then written
 * to the object store and auto-synced) or press Run import against whatever is
 * already under the prefix. The provider CSV/XLSX upload of the original flow
 * is still outstanding.
 *
 * <p>Summaries are carried in flash attributes rather than rendered from the
 * POST, so a refresh cannot repeat an upload or a sync.
 */
@Controller
public class AdminImportController {

    private static final Logger log = LoggerFactory.getLogger(AdminImportController.class);

    private final CatalogImportService importService;
    private final CatalogUploadService uploadService;

    public AdminImportController(CatalogImportService importService,
            CatalogUploadService uploadService) {
        this.importService = importService;
        this.uploadService = uploadService;
    }

    @GetMapping(Routes.ADMIN_IMPORT)
    public String importCatalog(Model model) {
        model.addAttribute("pageTitle", "Catalog Import");
        model.addAttribute("activeNav", "admin-import");
        model.addAttribute("importRunning", importService.isRunning());
        importService.lastRun().ifPresent(run -> model.addAttribute("lastRun", run));

        // A listing-only diff, so this is cheap enough to answer on each view.
        // It still talks to S3, and a screen that cannot be opened is worse than
        // one that cannot show the pending count.
        try {
            model.addAttribute("pending", importService.pendingChanges());
        } catch (CatalogStoreException e) {
            log.error("Could not inspect the staged catalog for P-06c", e);
            model.addAttribute("pendingError", e.getMessage());
        }
        return "admin/import";
    }

    @PostMapping(Routes.ADMIN_IMPORT_UPLOAD)
    public String upload(@RequestParam(value = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal MrsUserDetails actor,
            RedirectAttributes redirectAttributes) {

        UploadResult result = uploadService.upload(files,
                actor == null ? null : actor.getId());

        if (result.isEmptySelection()) {
            flash(redirectAttributes, "warning", Messages.UPLOAD_EMPTY);
            return "redirect:" + Routes.ADMIN_IMPORT;
        }

        if (result.uploaded() == 0) {
            flash(redirectAttributes, "danger", Messages.UPLOAD_ALL_REJECTED);
            redirectAttributes.addFlashAttribute("uploadRejected", result.rejected());
            return "redirect:" + Routes.ADMIN_IMPORT;
        }

        // Staging succeeded; the sync may still be refused or fail.
        if (!result.rejected().isEmpty()) {
            redirectAttributes.addFlashAttribute("uploadRejected", result.rejected());
        }

        ImportSummary sync = result.sync();
        if (sync == null) {
            flash(redirectAttributes, "success",
                    "%d song file(s) staged.".formatted(result.uploaded()));
        } else if (sync.alreadyRunning()) {
            flash(redirectAttributes, "warning", Messages.UPLOAD_SYNC_SKIPPED);
        } else if (sync.isFailed()) {
            flash(redirectAttributes, "danger",
                    "%d file(s) staged, but the import failed. Press Run import to retry."
                            .formatted(result.uploaded()));
        } else if (sync.isNoChange()) {
            // Unusual after a fresh put, but possible if ETags somehow match.
            flash(redirectAttributes, "success",
                    "%d file(s) staged; catalog was already in step."
                            .formatted(result.uploaded()));
        } else {
            flash(redirectAttributes, sync.skipped() > 0 || !result.rejected().isEmpty()
                            ? "warning" : "success",
                    "%d file(s) staged. Import: %d added, %d updated, %d skipped."
                            .formatted(result.uploaded(), sync.added(), sync.updated(),
                                    sync.skipped()));
            redirectAttributes.addFlashAttribute("summary", sync);
        }

        return "redirect:" + Routes.ADMIN_IMPORT;
    }

    @PostMapping(Routes.ADMIN_IMPORT_RUN)
    public String run(@RequestParam(defaultValue = "false") boolean force,
            @AuthenticationPrincipal MrsUserDetails actor,
            RedirectAttributes redirectAttributes) {

        ImportSummary summary = importService.sync(ImportTrigger.MANUAL,
                actor == null ? null : actor.getId(), force);

        if (summary.alreadyRunning()) {
            flash(redirectAttributes, "warning", Messages.IMPORT_ALREADY_RUNNING);
        } else if (summary.isFailed()) {
            flash(redirectAttributes, "danger", Messages.IMPORT_FAILED);
        } else if (summary.isNoChange()) {
            flash(redirectAttributes, "info", Messages.IMPORT_NO_CHANGE);
        } else {
            flash(redirectAttributes, summary.skipped() > 0 ? "warning" : "success",
                    "Import finished: %d added, %d updated, %d skipped."
                            .formatted(summary.added(), summary.updated(), summary.skipped()));
            // Only worth carrying when there is something to look at.
            redirectAttributes.addFlashAttribute("summary", summary);
        }

        return "redirect:" + Routes.ADMIN_IMPORT;
    }

    private void flash(RedirectAttributes redirectAttributes, String variant, String message) {
        redirectAttributes.addFlashAttribute("flash", message);
        redirectAttributes.addFlashAttribute("flashVariant", variant);
    }
}
