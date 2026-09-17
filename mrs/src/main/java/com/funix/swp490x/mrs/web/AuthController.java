package com.funix.swp490x.mrs.web;

import com.funix.swp490x.mrs.security.RequestRateLimiter;
import com.funix.swp490x.mrs.service.AuthService;
import com.funix.swp490x.mrs.service.AuthService.AccountRequestResult;
import com.funix.swp490x.mrs.service.AuthService.AccountRequestStatus;
import com.funix.swp490x.mrs.service.AuthService.PasswordResetResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * P-00 Login and P-01 Password Reset.
 *
 * <p>Login itself is handled by the Spring Security filter chain; this
 * controller only renders the screen and forwards mutating work to
 * {@link AuthService}. Which banner the login screen shows is driven by the
 * query parameter the security handlers redirect with (spec 4.1 screen states).
 */
@Controller
public class AuthController {

    private final AuthService authService;
    private final RequestRateLimiter registerRequestLimiter;

    public AuthController(AuthService authService, RequestRateLimiter registerRequestLimiter) {
        this.authService = authService;
        this.registerRequestLimiter = registerRequestLimiter;
    }

    /** P-00 Login / anonymous landing page. */
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
        return "auth/login";
    }

    /**
     * Landing-page register intent: captures one email and notifies ADMIN.
     *
     * <p>The destination mailbox is the configured main sender identity
     * ({@code mrs.mail.from}), so this shares the same operational mailbox used
     * by account-credentials delivery.
     *
     * <p>Capped per origin (BR-19, BV-12). The cap is taken before the address
     * is validated, so a loop of malformed values cannot walk past it, and no
     * mail leaves for a refused attempt (NFR-SEC07).
     */
    @PostMapping(Routes.REGISTER_REQUEST)
    public String registerRequest(@RequestParam String email, RedirectAttributes redirectAttributes,
            HttpServletRequest request, HttpServletResponse response, Model model) {

        if (!registerRequestLimiter.tryAcquire(clientKey(request))) {
            // Re-rendered rather than redirected so the 429 reaches the client
            // instead of being swallowed by a 302.
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            model.addAttribute("pageTitle", "Welcome");
            model.addAttribute("showLandingScene", true);
            model.addAttribute("reopenRegisterModal", true);
            model.addAttribute("registerEmailError", Messages.REGISTER_REQUEST_RATE_LIMITED);
            model.addAttribute("submittedRegisterEmail", email == null ? "" : email.trim());
            return "auth/login";
        }

        AccountRequestResult result = authService.requestAccount(email);
        if (result.status() == AccountRequestStatus.INVALID_EMAIL) {
            redirectAttributes.addFlashAttribute("registerEmailError", Messages.REGISTER_REQUEST_INVALID_EMAIL);
            redirectAttributes.addFlashAttribute("submittedRegisterEmail", result.email());
            redirectAttributes.addFlashAttribute("reopenRegisterModal", true);
            return "redirect:" + Routes.LOGIN;
        }
        if (result.status() == AccountRequestStatus.MAIL_FAILED) {
            redirectAttributes.addFlashAttribute("flashVariant", "warning");
            redirectAttributes.addFlashAttribute("flash", Messages.REGISTER_REQUEST_EMAIL_FAILED);
            redirectAttributes.addFlashAttribute("submittedRegisterEmail", result.email());
            redirectAttributes.addFlashAttribute("reopenRegisterModal", true);
            return "redirect:" + Routes.LOGIN;
        }
        redirectAttributes.addFlashAttribute("flashVariant", "success");
        redirectAttributes.addFlashAttribute("flash", Messages.REGISTER_REQUEST_SENT);
        return "redirect:" + Routes.LOGIN;
    }

    /** P-01 step 1 — request a reset link. */
    @GetMapping(Routes.PASSWORD_RESET)
    public String resetRequest(Model model) {
        model.addAttribute("pageTitle", "Reset your password");
        return "auth/password-reset-request";
    }

    /**
     * Issues a link when the address is registered, and reports the same
     * confirmation either way so the screen never discloses whether an account
     * exists (spec 4.2, FT-01).
     */
    @PostMapping(Routes.PASSWORD_RESET)
    public String resetRequestSubmit(@RequestParam String email, Model model) {
        authService.requestPasswordReset(email);

        model.addAttribute("pageTitle", "Reset your password");
        model.addAttribute("sent", true);
        model.addAttribute("email", email == null ? "" : email.trim());
        return "auth/password-reset-request";
    }

    /**
     * P-01 step 2 — set a new password from an emailed link.
     *
     * <p>A link past its validity window is gone rather than missing, so the
     * expired card is served with HTTP 410 (BV-01) instead of 200.
     */
    @GetMapping(Routes.PASSWORD_RESET_SET)
    public String resetSet(@RequestParam(required = false) String token, Model model,
            HttpServletResponse response) {
        boolean expired = !authService.isResetTokenValid(token);
        if (expired) {
            response.setStatus(HttpStatus.GONE.value());
        }
        model.addAttribute("pageTitle", "Reset your password");
        model.addAttribute("token", token);
        model.addAttribute("expired", expired);
        return "auth/password-reset-set";
    }

    /**
     * A password that fails BR-12 is rejected while the link stays usable for
     * the rest of its 30-minute window (FT-01 NAC-04).
     */
    @PostMapping(Routes.PASSWORD_RESET_SET)
    public String resetSetSubmit(@RequestParam(required = false) String token,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            Model model,
            HttpServletResponse response) {

        model.addAttribute("pageTitle", "Reset your password");
        model.addAttribute("token", token);

        PasswordResetResult result = authService.completePasswordReset(token, password, confirmPassword);
        if (result.succeeded()) {
            return "redirect:" + Routes.LOGIN + "?reset";
        }
        if (result.expired()) {
            response.setStatus(HttpStatus.GONE.value());
        }
        model.addAttribute("expired", result.expired());
        if (!result.violations().isEmpty()) {
            model.addAttribute("violations", result.violations());
        }
        return "auth/password-reset-set";
    }

    /**
     * Origin the account-request cap counts against. The instance sits behind
     * Nginx, so the first {@code X-Forwarded-For} hop is the real caller and
     * {@code getRemoteAddr} would otherwise be the proxy for every request.
     */
    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
