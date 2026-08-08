package com.funix.swp490x.mrs.web.api;

import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.security.PasswordPolicy;
import com.funix.swp490x.mrs.web.Routes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Forced password change on first login (FT-09) as JSON. */
@RestController
@RequestMapping(path = Routes.API_ACCOUNT_PASSWORD, produces = MediaType.APPLICATION_JSON_VALUE)
public class AccountPasswordRestController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AccountPasswordRestController(UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank String password,
            @NotBlank String confirmPassword) {
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<?> change(@AuthenticationPrincipal MrsUserDetails principal,
            @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        User user = userRepository.findByEmail(principal.getEmail()).orElseThrow();

        List<String> violations = new ArrayList<>();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            violations.add("Your current password is incorrect");
        }
        violations.addAll(PasswordPolicy.violations(request.password()));
        if (!request.password().equals(request.confirmPassword())) {
            violations.add("Both entries must match");
        }
        if (!violations.isEmpty()) {
            return ResponseEntity.unprocessableEntity()
                    .body(ApiError.of("This password was not accepted", violations));
        }

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        refreshAuthentication(user, httpRequest, httpResponse);

        return ResponseEntity.ok(Map.of(
                "message", "Password updated.",
                "redirectTo", user.getRole().getLandingPath()));
    }

    private void refreshAuthentication(User user, HttpServletRequest request,
            HttpServletResponse response) {
        request.changeSessionId();
        MrsUserDetails refreshed = new MrsUserDetails(user, true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                refreshed, null, refreshed.getAuthorities()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
