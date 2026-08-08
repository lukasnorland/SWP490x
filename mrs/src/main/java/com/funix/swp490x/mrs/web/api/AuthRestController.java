package com.funix.swp490x.mrs.web.api;

import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.mail.MailDeliveryException;
import com.funix.swp490x.mrs.mail.NotificationService;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.PasswordPolicy;
import com.funix.swp490x.mrs.security.PasswordResetTokenService;
import com.funix.swp490x.mrs.service.EmailPolicy;
import com.funix.swp490x.mrs.web.Messages;
import com.funix.swp490x.mrs.web.Routes;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth-adjacent REST actions for P-00 / P-01.
 *
 * <p>Sign-in itself stays on Spring Security's form login ({@code POST /login}).
 */
@RestController
@RequestMapping(path = Routes.API_AUTH, produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthRestController {

    private static final Logger log = LoggerFactory.getLogger(AuthRestController.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenService tokenService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

    public AuthRestController(UserRepository userRepository, PasswordResetTokenService tokenService,
            NotificationService notificationService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
    }

    public record EmailRequest(@NotBlank String email) {
    }

    public record ResetPasswordRequest(String token, @NotBlank String password,
            @NotBlank String confirmPassword) {
    }

    @PostMapping(path = "/register-requests", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> registerRequest(@RequestBody EmailRequest request) {
        String address = request.email() == null ? "" : request.email().trim();
        if (!EmailPolicy.isWellFormed(address)) {
            return ResponseEntity.unprocessableEntity()
                    .body(ApiError.of(Messages.REGISTER_REQUEST_INVALID_EMAIL));
        }

        try {
            notificationService.sendRegistrationRequest(address);
            return ResponseEntity.ok(new MessageResponse(Messages.REGISTER_REQUEST_SENT));
        } catch (MailDeliveryException e) {
            log.error("Could not deliver registration request for {}", address, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiError.of(Messages.REGISTER_REQUEST_EMAIL_FAILED));
        }
    }

    /**
     * Always returns the same confirmation so the API cannot disclose whether
     * the address is registered (FT-01).
     */
    @PostMapping(path = "/password-resets", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse requestPasswordReset(@RequestBody EmailRequest request) {
        String address = request.email() == null ? "" : request.email().trim();
        userRepository.findByEmail(address).ifPresent(this::sendResetLink);
        return new MessageResponse(
                "If that address belongs to an MRS account, a reset link is on its way. "
                        + "The link is valid for 30 minutes.");
    }

    @PutMapping(path = "/password-resets", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<?> completePasswordReset(@RequestBody ResetPasswordRequest request) {
        Optional<String> email = tokenService.emailFor(request.token());
        if (email.isEmpty()) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(ApiError.of("This reset link has expired. Request a new one."));
        }

        List<String> violations = new ArrayList<>(PasswordPolicy.violations(request.password()));
        if (!request.password().equals(request.confirmPassword())) {
            violations.add("Both entries must match");
        }
        if (!violations.isEmpty()) {
            return ResponseEntity.unprocessableEntity()
                    .body(ApiError.of("This password was not accepted", violations));
        }

        User user = userRepository.findByEmail(email.get()).orElseThrow();
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        tokenService.invalidate(request.token());

        return ResponseEntity.ok(Map.of(
                "message", "Password updated. Sign in with your new password.",
                "redirectTo", Routes.LOGIN + "?reset"));
    }

    private void sendResetLink(User user) {
        String token = tokenService.issue(user.getEmail());
        try {
            notificationService.sendPasswordResetLink(user.getEmail(), token);
        } catch (MailDeliveryException e) {
            log.error("Could not deliver the reset link for {}", user.getEmail(), e);
        }
    }
}
