package com.funix.swp490x.mrs.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forced password change HTML shell (FT-09). The update is
 * {@code PUT /api/account/password}.
 */
@Controller
public class AccountPasswordController {

    @GetMapping(Routes.PASSWORD_CHANGE)
    public String changeForm(Model model) {
        model.addAttribute("pageTitle", "Change your password");
        model.addAttribute("apiAccountPassword", Routes.API_ACCOUNT_PASSWORD);
        return "auth/password-change";
    }
}
