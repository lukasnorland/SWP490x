package com.funix.swp490x.mrs.service;

import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.mail.MailDeliveryException;
import com.funix.swp490x.mrs.mail.NotificationService;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.PasswordPolicy;
import com.funix.swp490x.mrs.security.PasswordResetTokenService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles account requests, password reset/change and profile updates (UC-38, UC-02/03, UC-34, UC-05). */
@Service
public class AuthService {

    /** {@code users.username} is VARCHAR(100); the profile form matches that. */
    public static final int DISPLAY_NAME_MAX_LENGTH = 100;

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenService tokenService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordResetTokenService tokenService,
            NotificationService notificationService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Notifies ADMIN of an account request without creating a record; returns validation/delivery outcomes.
     */
    public AccountRequestResult requestAccount(String email) {
        String address = email == null ? "" : email.trim();
        if (!EmailPolicy.isWellFormed(address)) {
            return AccountRequestResult.invalid(address);
        }
        try {
            notificationService.sendRegistrationRequest(address);
            return AccountRequestResult.sent(address);
        } catch (MailDeliveryException e) {
            log.error("Could not deliver registration request for {}", address, e);
            return AccountRequestResult.mailFailed(address);
        }
    }

    /** Requests a reset link; unknown addresses and delivery failures keep the same confirmation. */
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmail(email.trim()).ifPresent(this::sendResetLink);
    }

    public boolean isResetTokenValid(String token) {
        return tokenService.emailFor(token).isPresent();
    }

    /** Resets the password from a valid token; BR-12 rejection leaves the token usable until expiry. */
    @Transactional
    public PasswordResetResult completePasswordReset(String token, String password,
            String confirmPassword) {

        Optional<String> email = tokenService.emailFor(token);
        if (email.isEmpty()) {
            return PasswordResetResult.ofExpired();
        }

        List<String> violations = passwordViolations(password, confirmPassword);
        if (!violations.isEmpty()) {
            return PasswordResetResult.rejected(violations);
        }

        User user = userRepository.findByEmail(email.get()).orElseThrow();
        applyPassword(user, password);
        tokenService.invalidate(token);
        return PasswordResetResult.ok();
    }

    /** Changes the authenticated account password after checking the current password (UC-34, UC-05). */
    @Transactional
    public PasswordChangeResult changePassword(String email, String currentPassword,
            String password, String confirmPassword) {

        User user = userRepository.findByEmail(email).orElseThrow();
        List<String> violations = new ArrayList<>();
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            violations.add("Your current password is incorrect");
        } else if (passwordEncoder.matches(password, user.getPasswordHash())) {
            violations.add("Choose a password you have not used before");
        }
        violations.addAll(passwordViolations(password, confirmPassword));
        if (!violations.isEmpty()) {
            return PasswordChangeResult.rejected(violations);
        }
        applyPassword(user, password);
        return PasswordChangeResult.ok(user.getRole().getLandingPath());
    }

    /** Updates only the signed-in account display name; role and email remain unchanged. */
    @Transactional
    public DisplayNameResult updateDisplayName(String email, String displayName) {
        String name = displayName == null ? "" : displayName.trim();
        List<String> violations = displayNameViolations(name);
        if (!violations.isEmpty()) {
            return DisplayNameResult.rejected(violations);
        }
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setUsername(name);
        userRepository.save(user);
        return DisplayNameResult.ok(name);
    }

    private void sendResetLink(User user) {
        String token = tokenService.issue(user.getEmail());
        try {
            notificationService.sendPasswordResetLink(user.getEmail(), token);
        } catch (MailDeliveryException e) {
            log.error("Could not deliver the reset link for {}", user.getEmail(), e);
        }
    }

    private void applyPassword(User user, String password) {
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    private static List<String> passwordViolations(String password, String confirmPassword) {
        List<String> violations = new ArrayList<>(PasswordPolicy.violations(password));
        if (!password.equals(confirmPassword)) {
            violations.add("Both entries must match");
        }
        return violations;
    }

    private static List<String> displayNameViolations(String name) {
        List<String> violations = new ArrayList<>();
        if (name.isEmpty()) {
            violations.add("Enter a display name");
        } else if (name.length() > DISPLAY_NAME_MAX_LENGTH) {
            violations.add("Use at most %d characters".formatted(DISPLAY_NAME_MAX_LENGTH));
        }
        return violations;
    }

    public enum AccountRequestStatus {
        INVALID_EMAIL,
        SENT,
        MAIL_FAILED
    }

    public record AccountRequestResult(AccountRequestStatus status, String email) {

        static AccountRequestResult invalid(String email) {
            return new AccountRequestResult(AccountRequestStatus.INVALID_EMAIL, email);
        }

        static AccountRequestResult sent(String email) {
            return new AccountRequestResult(AccountRequestStatus.SENT, email);
        }

        static AccountRequestResult mailFailed(String email) {
            return new AccountRequestResult(AccountRequestStatus.MAIL_FAILED, email);
        }
    }

    public record PasswordResetResult(boolean expired, List<String> violations) {

        static PasswordResetResult ofExpired() {
            return new PasswordResetResult(true, List.of());
        }

        static PasswordResetResult rejected(List<String> violations) {
            return new PasswordResetResult(false, List.copyOf(violations));
        }

        static PasswordResetResult ok() {
            return new PasswordResetResult(false, List.of());
        }

        public boolean succeeded() {
            return !expired && violations.isEmpty();
        }
    }

    public record PasswordChangeResult(List<String> violations, String landingPath) {

        static PasswordChangeResult rejected(List<String> violations) {
            return new PasswordChangeResult(List.copyOf(violations), null);
        }

        static PasswordChangeResult ok(String landingPath) {
            return new PasswordChangeResult(List.of(), landingPath);
        }

        public boolean succeeded() {
            return violations.isEmpty();
        }
    }

    public record DisplayNameResult(String displayName, List<String> violations) {

        static DisplayNameResult rejected(List<String> violations) {
            return new DisplayNameResult(null, List.copyOf(violations));
        }

        static DisplayNameResult ok(String displayName) {
            return new DisplayNameResult(displayName, List.of());
        }

        public boolean succeeded() {
            return violations.isEmpty();
        }
    }
}
