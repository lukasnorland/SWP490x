package com.funix.swp490x.mrs.security;

import com.funix.swp490x.mrs.service.SettingsService;
import com.funix.swp490x.mrs.settings.SettingKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Issues the time-limited reset links behind P-01. Validity is the P-06d
 * setting (default BV-01: 30 minutes).
 *
 * <p>Tokens live in memory, so they do not survive a restart. That is a
 * deliberate placeholder: a {@code password_reset_token} table replaces this
 * class without touching the screens.
 */
@Service
public class PasswordResetTokenService {

    public static final Duration DEFAULT_VALIDITY =
            Duration.ofMinutes(SettingKey.RESET_LINK_MINUTES.defaultInt());

    private final SettingsService settings;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Token> tokens = new ConcurrentHashMap<>();

    public PasswordResetTokenService(ObjectProvider<SettingsService> settings) {
        this.settings = settings.getIfAvailable();
    }

    /** Tests that do not exercise P-06d use the BV-01 default. */
    PasswordResetTokenService() {
        this.settings = null;
    }

    public Duration validity() {
        return settings != null ? settings.resetLinkValidity() : DEFAULT_VALIDITY;
    }

    /**
     * Creates a token for the address. Callers must not vary their response on
     * whether the address exists — P-01 shows the same confirmation either way.
     */
    public String issue(String email) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.put(token, new Token(email, Instant.now().plus(validity())));
        return token;
    }

    /** The address a still-valid token belongs to, or empty when expired/unknown. */
    public Optional<String> emailFor(String token) {
        if (token == null) {
            return Optional.empty();
        }
        Token issued = tokens.get(token);
        if (issued == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(issued.expiresAt())) {
            tokens.remove(token);
            return Optional.empty();
        }
        return Optional.of(issued.email());
    }

    /** Consumes the token once the new password has been accepted. */
    public void invalidate(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }

    private record Token(String email, Instant expiresAt) {
    }
}
