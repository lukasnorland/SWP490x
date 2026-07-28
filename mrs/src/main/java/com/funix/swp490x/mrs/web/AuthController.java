package com.funix.swp490x.mrs.web;

import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.PasswordPolicy;
import com.funix.swp490x.mrs.security.PasswordResetTokenService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * P-00 Login and P-01 Password Reset.
 *
 * <p>Login itself is handled by the Spring Security filter chain; this
 * controller only renders the screen. Which banner it shows is driven by the
 * query parameter the security handlers redirect with (spec 4.1 screen states).
 */
@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordResetTokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordResetTokenService tokenService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
    }

    /** P-00 Login. */
    @GetMapping(Routes.LOGIN)
    public String login(Model model) {
        model.addAttribute("pageTitle", "Sign in");
        return "auth/login";
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
        userRepository.findByEmail(email.trim())
                .ifPresent(user -> tokenService.issue(user.getEmail()));

        model.addAttribute("pageTitle", "Reset your password");
        model.addAttribute("sent", true);
        model.addAttribute("email", email.trim());
        return "auth/password-reset-request";
    }

    /** P-01 step 2 — set a new password from an emailed link. */
    @GetMapping(Routes.PASSWORD_RESET_SET)
    public String resetSet(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("pageTitle", "Reset your password");
        model.addAttribute("token", token);
        model.addAttribute("expired", tokenService.emailFor(token).isEmpty());
        return "auth/password-reset-set";
    }

    /**
     * A password that fails BR-12 is rejected while the link stays usable for
     * the rest of its 30-minute window (FT-01 NAC-04).
     */
    @PostMapping(Routes.PASSWORD_RESET_SET)
    @Transactional
    public String resetSetSubmit(@RequestParam(required = false) String token,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            Model model) {

        model.addAttribute("pageTitle", "Reset your password");
        model.addAttribute("token", token);

        Optional<String> email = tokenService.emailFor(token);
        if (email.isEmpty()) {
            model.addAttribute("expired", true);
            return "auth/password-reset-set";
        }

        List<String> violations = new ArrayList<>(PasswordPolicy.violations(password));
        if (!password.equals(confirmPassword)) {
            violations.add("Both entries must match");
        }
        if (!violations.isEmpty()) {
            model.addAttribute("expired", false);
            model.addAttribute("violations", violations);
            return "auth/password-reset-set";
        }

        User user = userRepository.findByEmail(email.get()).orElseThrow();
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setMustChangePassword(false);
        userRepository.save(user);
        tokenService.invalidate(token);

        return "redirect:" + Routes.LOGIN + "?reset";
    }
}
