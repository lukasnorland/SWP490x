package com.funix.swp490x.mrs.web.admin;

import com.funix.swp490x.mrs.service.AdminLogService;
import com.funix.swp490x.mrs.web.Routes;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * P-06e — Audit & Recommendation Log (UC-32).
 *
 * <p>Access is granted by SecurityConfig on {@code /admin/**}, so a non-ADMIN
 * request is rejected before any handler here runs (FT-09 NAC-02, BR-01).
 */
@Controller
public class AdminLogsController {

    private final AdminLogService adminLogService;

    public AdminLogsController(AdminLogService adminLogService) {
        this.adminLogService = adminLogService;
    }

    @GetMapping(Routes.ADMIN_LOGS)
    public String logs(@RequestParam(defaultValue = AdminLogService.TAB_AUDIT) String tab,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String llm,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        boolean recommendation = AdminLogService.isRecommendationTab(tab);
        model.addAttribute("pageTitle", "Audit & Recommendation Log");
        model.addAttribute("activeNav", "admin-logs");
        model.addAttribute("tab", recommendation
                ? AdminLogService.TAB_RECOMMENDATION
                : AdminLogService.TAB_AUDIT);
        model.addAttribute("filterActorId", actorId);
        model.addAttribute("filterUserId", userId);
        model.addAttribute("filterAction", action == null ? "" : action);
        model.addAttribute("filterLlm", llm == null ? "" : llm);
        model.addAttribute("filterFrom", from);
        model.addAttribute("filterTo", to);
        model.addAttribute("actions", adminLogService.actions());
        model.addAttribute("actors", adminLogService.actors());
        model.addAttribute("searchUsers", adminLogService.searchUsers());

        if (recommendation) {
            model.addAttribute("recommendations",
                    adminLogService.searchRecommendations(userId, llm, from, to, page));
        } else {
            model.addAttribute("auditEntries",
                    adminLogService.searchAudit(actorId, action, from, to, page));
        }
        return "admin/logs";
    }
}
