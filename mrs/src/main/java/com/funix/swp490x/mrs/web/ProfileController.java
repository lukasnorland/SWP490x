package com.funix.swp490x.mrs.web;

import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.service.AuthService;
import com.funix.swp490x.mrs.service.AuthService.DisplayNameResult;
import com.funix.swp490x.mrs.service.AuthService.PasswordChangeResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * P-05 My Profile (FT-02). UC-04 is view-own-account; UC-05 is display name
 * and password. Never writes role (BR-01). Resume playlist work on P-03a.
 */
@Controller
public class ProfileController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public ProfileController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping(Routes.PROFILE)
    public String profile(Model model) {
        return render(model);
    }

    /**
     * UC-05 NAC-01: a direct POST at the profile URL (typically carrying a
     * {@code role} field) is refused; the account's role is never written here.
     */
    @PostMapping(Routes.PROFILE)
    public void rejectOwnRoleChange(HttpServletRequest request) {
        rejectRoleTampering(request);
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    @PostMapping(Routes.PROFILE_NAME)
    public String updateDisplayName(@AuthenticationPrincipal MrsUserDetails principal,
            @RequestParam String displayName,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {

        rejectRoleTampering(request);

        DisplayNameResult result = authService.updateDisplayName(principal.getEmail(), displayName);
        if (!result.succeeded()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            model.addAttribute("nameViolations", result.violations());
            model.addAttribute("submittedDisplayName", displayName);
            return render(model);
        }

        refreshAuthentication(principal.withDisplayName(result.displayName()), request, response,
                false);
        flash(redirectAttributes, "success", Messages.PROFILE_NAME_SAVED);
        return "redirect:" + Routes.PROFILE;
    }

    @PostMapping(Routes.PROFILE_PASSWORD)
    public String updatePassword(@AuthenticationPrincipal MrsUserDetails principal,
            @RequestParam String currentPassword,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {

        rejectRoleTampering(request);

        PasswordChangeResult result = authService.changePassword(
                principal.getEmail(), currentPassword, password, confirmPassword);
        if (!result.succeeded()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            model.addAttribute("violations", result.violations());
            return render(model);
        }

        // Credential just changed, so the session id rotates. The user stays
        // on P-05 — AC-01 does not require a re-login.
        refreshAuthentication(principal, request, response, true);
        flash(redirectAttributes, "success", Messages.PROFILE_PASSWORD_SAVED);
        return "redirect:" + Routes.PROFILE;
    }

    private static String render(Model model) {
        model.addAttribute("pageTitle", "My Profile");
        model.addAttribute("activeNav", "profile");
        return "profile/index";
    }

    /**
     * Presence of a {@code role} field is enough: the screen never offers one,
     * so submitting it is the NAC-01 probe.
     */
    private static void rejectRoleTampering(HttpServletRequest request) {
        if (request.getParameterMap().containsKey("role")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private void refreshAuthentication(MrsUserDetails refreshed, HttpServletRequest request,
            HttpServletResponse response, boolean rotateSession) {
        if (rotateSession) {
            request.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                refreshed, null, refreshed.getAuthorities()));

        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private static void flash(RedirectAttributes redirectAttributes, String variant,
            String message) {
        redirectAttributes.addFlashAttribute("flash", message);
        redirectAttributes.addFlashAttribute("flashVariant", variant);
    }
}
