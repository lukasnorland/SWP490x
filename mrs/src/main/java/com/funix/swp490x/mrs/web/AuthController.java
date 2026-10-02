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

/** Renders login/reset screens and delegates account-request and password work to {@link AuthService}. */
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

    /** Sends an account request to the ADMIN mailbox; rate-limits before validation (UC-38, BR-19). */
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

    /** Renders the reset form; invalid or expired tokens receive HTTP 410 (BV-01). */
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

    /** BR-12 rejection leaves the reset token valid for the remainder of its configured window. */
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

    /** Uses the first forwarded address for the account-request limit when behind the deployment proxy. */
    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
