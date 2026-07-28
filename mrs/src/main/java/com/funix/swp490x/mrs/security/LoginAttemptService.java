package com.funix.swp490x.mrs.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Brute-force lockout for the login screen: five failed attempts inside a
 * fifteen-minute window lock the account for the rest of that window
 * (FT-01 NAC-01, rendered as the P-00 "locked" state).
 *
 * <p>State is in-memory, so it is per instance. That is adequate for the
 * single-instance deployment target; a shared store would be needed before
 * running more than one node. The thresholds will move to System Settings
 * (P-06d) once that screen is backed by persistence.
 */
@Service
public class LoginAttemptService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Attempts> attemptsByEmail = new ConcurrentHashMap<>();

    public boolean isLocked(String email) {
        return lockRemaining(email) != null;
    }

    /** Time left in the lockout window, or {@code null} when not locked. */
    public Duration lockRemaining(String email) {
        if (email == null) {
            return null;
        }
        Attempts attempts = attemptsByEmail.get(key(email));
        if (attempts == null) {
            return null;
        }
        synchronized (attempts) {
            if (attempts.lockedUntil == null) {
                return null;
            }
            Duration remaining = Duration.between(Instant.now(), attempts.lockedUntil);
            if (remaining.isNegative() || remaining.isZero()) {
                attemptsByEmail.remove(key(email));
                return null;
            }
            return remaining;
        }
    }

    /** Records a failed attempt and reports whether it triggered the lockout. */
    public boolean recordFailure(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        Attempts attempts = attemptsByEmail.computeIfAbsent(key(email), ignored -> new Attempts());
        synchronized (attempts) {
            Instant now = Instant.now();
            if (attempts.windowStart == null || now.isAfter(attempts.windowStart.plus(WINDOW))) {
                attempts.windowStart = now;
                attempts.count = 0;
                attempts.lockedUntil = null;
            }
            attempts.count++;
            if (attempts.count >= MAX_ATTEMPTS) {
                attempts.lockedUntil = now.plus(WINDOW);
                return true;
            }
            return false;
        }
    }

    /** Clears the counter after a successful sign-in. */
    public void reset(String email) {
        if (email != null) {
            attemptsByEmail.remove(key(email));
        }
    }

    private String key(String email) {
        return email.trim().toLowerCase();
    }

    private static final class Attempts {
        private int count;
        private Instant windowStart;
        private Instant lockedUntil;
    }
}
