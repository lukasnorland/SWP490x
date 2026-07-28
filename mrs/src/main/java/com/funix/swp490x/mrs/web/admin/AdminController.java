package com.funix.swp490x.mrs.web.admin;

import com.funix.swp490x.mrs.web.Routes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The ADMIN area, P-06a – P-06e.
 *
 * <p>Access is granted by SecurityConfig on {@code /admin/**}, so a non-ADMIN
 * request is rejected before any handler here runs (FT-09 NAC-02, BR-01).
 */
@Controller
public class AdminController {

    /** P-06a — User Management, the ADMIN landing page. */
    @GetMapping(Routes.ADMIN_USERS)
    public String users(Model model) {
        model.addAttribute("pageTitle", "User Management");
        model.addAttribute("activeNav", "admin-users");
        return "admin/users";
    }

    /** P-06b — Song Catalog & Metadata (tabs: Songs · Tags). */
    @GetMapping(Routes.ADMIN_CATALOG)
    public String catalog(Model model) {
        model.addAttribute("pageTitle", "Song Catalog & Metadata");
        model.addAttribute("activeNav", "admin-catalog");
        return "admin/catalog";
    }

    /** P-06c — Catalog Import (Upload · Preview · Summary). */
    @GetMapping(Routes.ADMIN_IMPORT)
    public String importCatalog(Model model) {
        model.addAttribute("pageTitle", "Catalog Import");
        model.addAttribute("activeNav", "admin-import");
        return "admin/import";
    }

    /** P-06d — System Settings. */
    @GetMapping(Routes.ADMIN_SETTINGS)
    public String settings(Model model) {
        model.addAttribute("pageTitle", "System Settings");
        model.addAttribute("activeNav", "admin-settings");
        return "admin/settings";
    }

    /** P-06e — Audit & Recommendation Log (tabs: Audit · Recommendation). */
    @GetMapping(Routes.ADMIN_LOGS)
    public String logs(Model model) {
        model.addAttribute("pageTitle", "Audit & Recommendation Log");
        model.addAttribute("activeNav", "admin-logs");
        return "admin/logs";
    }
}
