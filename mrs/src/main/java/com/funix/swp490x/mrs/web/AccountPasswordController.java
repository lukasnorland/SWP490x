package com.funix.swp490x.mrs.web;

import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.service.AuthService;
import com.funix.swp490x.mrs.service.AuthService.PasswordChangeResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Forced password change on first login (FT-09, flow F-04).
 *
 * <p>ForcedPasswordChangeInterceptor funnels every other request here until the
 * account has set its own password. On success the session principal is
 * refreshed so the stale must-change flag cannot bounce the user back here.
 */
@Controller
public class AccountPasswordController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AccountPasswordController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping(Routes.PASSWORD_CHANGE)
    public String changeForm(Model model) {
        model.addAttribute("pageTitle", "Change your password");
        return "auth/password-change";
    }

    @PostMapping(Routes.PASSWORD_CHANGE)
    public String change(@AuthenticationPrincipal MrsUserDetails principal,
            @RequestParam String currentPassword,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {

        model.addAttribute("pageTitle", "Change your password");

        PasswordChangeResult result = authService.changePassword(
                principal.getEmail(), currentPassword, password, confirmPassword);
        if (!result.succeeded()) {
            model.addAttribute("violations", result.violations());
            return "auth/password-change";
        }

        // Flow F-04 continues straight to the role landing page, so the session
        // is kept and its principal refreshed — otherwise the stale
        // must-change flag would bounce every request back here.
        refreshAuthentication(principal.withoutForcedPasswordChange(), request, response);
        return "redirect:" + result.landingPath();
    }

    private void refreshAuthentication(MrsUserDetails refreshed, HttpServletRequest request,
            HttpServletResponse response) {
        // The credential just changed, so the session gets a new id.
        request.changeSessionId();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                refreshed, null, refreshed.getAuthorities()));

        SecurityContextHolder.setContext(context);
        // Spring Security 6+ no longer saves the context implicitly.
        securityContextRepository.saveContext(context, request, response);
    }
}
