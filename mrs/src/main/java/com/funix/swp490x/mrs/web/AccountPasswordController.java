package com.funix.swp490x.mrs.web;

import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.security.PasswordPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Forced password change on first login (FT-09, flow F-04).
 *
 * <p>ForcedPasswordChangeInterceptor funnels every other request here until the
 * account has set its own password. On success the session is dropped so the
 * user signs in again with the new credential.
 */
@Controller
public class AccountPasswordController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AccountPasswordController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping(Routes.PASSWORD_CHANGE)
    public String changeForm(Model model) {
        model.addAttribute("pageTitle", "Change your password");
        return "auth/password-change";
    }

    @PostMapping(Routes.PASSWORD_CHANGE)
    @Transactional
    public String change(@AuthenticationPrincipal MrsUserDetails principal,
            @RequestParam String currentPassword,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {

        model.addAttribute("pageTitle", "Change your password");

        User user = userRepository.findByEmail(principal.getEmail()).orElseThrow();

        List<String> violations = new ArrayList<>();
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            violations.add("Your current password is incorrect");
        }
        violations.addAll(PasswordPolicy.violations(password));
        if (!password.equals(confirmPassword)) {
            violations.add("Both entries must match");
        }
        if (!violations.isEmpty()) {
            model.addAttribute("violations", violations);
            return "auth/password-change";
        }

        user.setPasswordHash(passwordEncoder.encode(password));
        user.setMustChangePassword(false);
        userRepository.save(user);

        // Flow F-04 continues straight to the role landing page, so the session
        // is kept and its principal refreshed — otherwise the stale
        // must-change flag would bounce every request back here.
        refreshAuthentication(user, request, response);

        return "redirect:" + user.getRole().getLandingPath();
    }

    private void refreshAuthentication(User user, HttpServletRequest request, HttpServletResponse response) {
        // The credential just changed, so the session gets a new id.
        request.changeSessionId();

        MrsUserDetails refreshed = new MrsUserDetails(user, true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                refreshed, null, refreshed.getAuthorities()));

        SecurityContextHolder.setContext(context);
        // Spring Security 6+ no longer saves the context implicitly.
        securityContextRepository.saveContext(context, request, response);
    }
}
