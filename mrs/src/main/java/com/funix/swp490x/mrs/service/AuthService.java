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

/**
 * Authentication use cases that sit outside Spring Security's form-login chain:
 * the public account request (UC-38), password reset (UC-02 / UC-03), the
 * forced first-login change (UC-34), and profile self-service (UC-05).
 *
 * <p>The web layer must not call {@link UserRepository} for these flows (TDS
 * Part 1.3); this service owns the transaction and the mail side-effects.
 */
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
     * UC-38: notify ADMIN of a registration intent. Creates nothing.
     *
     * <p>A malformed address is rejected before mail is attempted. A delivery
     * failure is returned rather than thrown so the login screen can keep the
     * same confirmation shape without depending on the mail package.
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

    /**
     * UC-02: issue a reset link when the address is registered.
     *
     * <p>Unknown addresses and delivery failures are swallowed so the screen
     * never discloses whether an account exists (FT-01).
     */
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmail(email.trim()).ifPresent(this::sendResetLink);
    }

    public boolean isResetTokenValid(String token) {
        return tokenService.emailFor(token).isPresent();
    }

    /**
     * UC-03: set a new password from an emailed link.
     *
     * <p>A password that fails BR-12 is rejected while the link stays usable
     * for the rest of its 30-minute window (FT-01 NAC-04).
     */
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

    /**
     * UC-34: replace the ADMIN-issued password after first login.
     *
     * @param email the authenticated account; looked up rather than trusted
     *              from the session principal's other fields
     */
    @Transactional
    public PasswordChangeResult changePassword(String email, String currentPassword,
            String password, String confirmPassword) {

        User user = userRepository.findByEmail(email).orElseThrow();
        List<String> violations = new ArrayList<>();
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            violations.add("Your current password is incorrect");
        }
        violations.addAll(passwordViolations(password, confirmPassword));
        if (!violations.isEmpty()) {
            return PasswordChangeResult.rejected(violations);
        }
        applyPassword(user, password);
        return PasswordChangeResult.ok(user.getRole().getLandingPath());
    }

    /**
     * UC-05: replace the signed-in account's display name. Role and email are
     * not accepted here — only ADMIN changes those (BR-01, NAC-01).
     */
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
