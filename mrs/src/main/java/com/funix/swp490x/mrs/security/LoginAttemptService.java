package com.funix.swp490x.mrs.security;

import com.funix.swp490x.mrs.service.SettingsService;
import com.funix.swp490x.mrs.settings.SettingKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Brute-force lockout for the login screen. Threshold and window come from
 * System Settings (P-06d / UC-31); the defaults match BV-06 (5 / 15 min).
 *
 * <p>State is in-memory, so it is per instance. That is adequate for the
 * single-instance deployment target; a shared store would be needed before
 * running more than one node.
 */
@Service
public class LoginAttemptService {

    private final SettingsService settings;
    private final Map<String, Attempts> attemptsByEmail = new ConcurrentHashMap<>();

    public LoginAttemptService(ObjectProvider<SettingsService> settings) {
        this.settings = settings.getIfAvailable();
    }

    /** Tests that do not exercise P-06d use the BV-06 defaults. */
    LoginAttemptService() {
        this.settings = null;
    }

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
            Duration window = lockoutWindow();
            if (attempts.windowStart == null || now.isAfter(attempts.windowStart.plus(window))) {
                attempts.windowStart = now;
                attempts.count = 0;
                attempts.lockedUntil = null;
            }
            attempts.count++;
            if (attempts.count >= lockoutThreshold()) {
                attempts.lockedUntil = now.plus(window);
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

    private int lockoutThreshold() {
        return settings != null
                ? settings.lockoutThreshold()
                : SettingKey.LOCKOUT_THRESHOLD.defaultInt();
    }

    private Duration lockoutWindow() {
        return settings != null
                ? settings.lockoutWindow()
                : Duration.ofMinutes(SettingKey.LOCKOUT_WINDOW_MINUTES.defaultInt());
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
