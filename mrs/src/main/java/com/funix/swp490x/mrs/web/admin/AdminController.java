package com.funix.swp490x.mrs.web.admin;

import com.funix.swp490x.mrs.web.Routes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The ADMIN area, P-06d – P-06e. P-06a, P-06b and P-06f have their own
 * controllers now that they do more than render.
 *
 * <p>Access is granted by SecurityConfig on {@code /admin/**}, so a non-ADMIN
 * request is rejected before any handler here runs (FT-09 NAC-02, BR-01).
 */
@Controller
public class AdminController {

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
