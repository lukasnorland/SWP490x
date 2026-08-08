package com.funix.swp490x.mrs.web.admin;

import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.security.InitialPasswordGenerator;
import com.funix.swp490x.mrs.service.UserAccountService;
import com.funix.swp490x.mrs.web.Routes;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * P-06a HTML shell. Account mutations live on
 * {@link com.funix.swp490x.mrs.web.api.admin.AdminUserRestController}.
 *
 * <p>This controller only renders the screen (with an SSR first page so the
 * table is readable without waiting on a second round trip). Filters remain
 * ordinary GET query params so browsing works with JavaScript disabled.
 */
@Controller
public class AdminUserController {

    private final UserAccountService userAccountService;
    private final ObjectProvider<JavaMailSender> mailSender;

    public AdminUserController(UserAccountService userAccountService,
            ObjectProvider<JavaMailSender> mailSender) {
        this.userAccountService = userAccountService;
        this.mailSender = mailSender;
    }

    @GetMapping(Routes.ADMIN_USERS)
    public String users(@RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        Page<User> users = userAccountService.search(role, status, q, page);
        model.addAttribute("pageTitle", "User Management");
        model.addAttribute("activeNav", "admin-users");
        model.addAttribute("users", users);
        model.addAttribute("filterRole", role);
        model.addAttribute("filterStatus", status);
        model.addAttribute("filterQuery", q == null ? "" : q);
        model.addAttribute("filterRoles", Role.values());
        model.addAttribute("filterStatuses", UserStatus.values());
        model.addAttribute("assignableRoles", UserAccountService.ASSIGNABLE_ROLES);
        model.addAttribute("apiUsersBase", Routes.API_ADMIN_USERS);
        model.addAttribute("suggestedPassword", InitialPasswordGenerator.generate());
        model.addAttribute("requireSesRecipient", mailSender.getIfAvailable() != null);
        return "admin/users";
    }
}
