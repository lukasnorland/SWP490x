package com.funix.swp490x.mrs.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Issues the time-limited reset links behind P-01 (BV-01: valid for 30 minutes).
 *
 * <p>No mail transport is configured yet, so the link is written to the
 * application log instead of being emailed. Tokens live in memory, which means
 * they do not survive a restart. Both are deliberate placeholders: swapping in
 * a mail sender and a {@code password_reset_token} table replaces this class
 * without touching the screens.
 */
@Service
public class PasswordResetTokenService {

    public static final Duration VALIDITY = Duration.ofMinutes(30);

    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenService.class);

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Token> tokens = new ConcurrentHashMap<>();

    /**
     * Creates a token for the address. Callers must not vary their response on
     * whether the address exists — P-01 shows the same confirmation either way.
     */
    public String issue(String email) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.put(token, new Token(email, Instant.now().plus(VALIDITY)));

        log.info("Password reset link for {} (valid {} minutes): /password-reset/set?token={}",
                email, VALIDITY.toMinutes(), token);
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
