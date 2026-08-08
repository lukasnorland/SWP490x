package com.funix.swp490x.mrs.web;

import com.funix.swp490x.mrs.security.PasswordResetTokenService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * P-00 / P-01 HTML shells. Mutations live on
 * {@link com.funix.swp490x.mrs.web.api.AuthRestController}.
 *
 * <p>Sign-in itself is still Spring Security form login ({@code POST /login}).
 */
@Controller
public class AuthController {

    private final PasswordResetTokenService tokenService;

    public AuthController(PasswordResetTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GetMapping(Routes.LOGIN)
    public String login(@RequestParam(required = false) String error,
            @RequestParam(required = false) String locked,
            @RequestParam(required = false) String expired,
            @RequestParam(required = false) String logout,
            @RequestParam(required = false) String reset,
            Model model) {

        model.addAttribute("pageTitle", "Welcome");
        model.addAttribute("openLoginModal", error != null || locked != null || expired != null
                || logout != null || reset != null);
        model.addAttribute("showLandingScene", true);
        model.addAttribute("apiAuthBase", Routes.API_AUTH);
        return "auth/login";
    }

    @GetMapping(Routes.PASSWORD_RESET)
    public String resetRequest(@RequestParam(required = false) String sent, Model model) {
        model.addAttribute("pageTitle", "Reset your password");
        model.addAttribute("sent", sent != null);
        model.addAttribute("apiAuthBase", Routes.API_AUTH);
        return "auth/password-reset-request";
    }

    @GetMapping(Routes.PASSWORD_RESET_SET)
    public String resetSet(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("pageTitle", "Reset your password");
        model.addAttribute("token", token);
        model.addAttribute("expired", tokenService.emailFor(token).isEmpty());
        model.addAttribute("apiAuthBase", Routes.API_AUTH);
        return "auth/password-reset-set";
    }
}
