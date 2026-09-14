package com.funix.swp490x.mrs.security;

import com.funix.swp490x.mrs.service.SettingsService;
import com.funix.swp490x.mrs.settings.SettingKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Fixed-window request cap for the unauthenticated account-request form
 * (BR-19, NFR-SEC07). Limit and window come from System Settings
 * (P-06d / UC-31); the defaults match BV-12 (3 per hour per origin).
 *
 * <p>State is in-memory, so it is per instance, exactly as
 * {@link LoginAttemptService}. That is adequate for the single-instance
 * deployment target; a shared store would be needed before running more than
 * one node.
 */
@Service
public class RequestRateLimiter {

    /**
     * Distinct keys held before expired windows are swept. The key is a
     * caller-supplied address, so the map must not grow without bound.
     */
    private static final int SWEEP_THRESHOLD = 1024;

    /** Bucket for a request that arrived with no usable address. */
    private static final String UNKNOWN_KEY = "unknown";

    private final SettingsService settings;
    private final Clock clock;
    private final Map<String, Window> windowsByKey = new ConcurrentHashMap<>();

    /** Marked explicitly: the test constructor below is the other candidate. */
    @Autowired
    public RequestRateLimiter(ObjectProvider<SettingsService> settings) {
        this(settings.getIfAvailable(), Clock.systemUTC());
    }

    /** Tests that do not exercise P-06d use the BV-12 defaults. */
    RequestRateLimiter(SettingsService settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * Records one attempt against {@code key} and reports whether it is within
     * the limit. Counts attempts rather than successes, so the caller has to
     * call this before validating the request: otherwise a malformed-input
     * loop would never be capped.
     *
     * @return {@code true} when the attempt is allowed
     */
    public boolean tryAcquire(String key) {
        String bucket = StringUtils.hasText(key) ? key.trim() : UNKNOWN_KEY;
        if (windowsByKey.size() > SWEEP_THRESHOLD) {
            sweepExpired();
        }
        Window entry = windowsByKey.computeIfAbsent(bucket, ignored -> new Window());
        synchronized (entry) {
            Instant now = clock.instant();
            if (entry.startedAt == null || expired(entry, now)) {
                entry.startedAt = now;
                entry.count = 0;
            }
            entry.count++;
            return entry.count <= limit();
        }
    }

    private void sweepExpired() {
        Instant now = clock.instant();
        windowsByKey.values().removeIf(entry -> {
            synchronized (entry) {
                return entry.startedAt == null || expired(entry, now);
            }
        });
    }

    private boolean expired(Window entry, Instant now) {
        return !now.isBefore(entry.startedAt.plus(windowLength()));
    }

    private int limit() {
        return settings != null
                ? settings.accountRequestLimit()
                : SettingKey.ACCOUNT_REQUEST_LIMIT.defaultInt();
    }

    private Duration windowLength() {
        return settings != null
                ? settings.accountRequestWindow()
                : Duration.ofMinutes(SettingKey.ACCOUNT_REQUEST_WINDOW_MINUTES.defaultInt());
    }

    private static final class Window {
        private int count;
        private Instant startedAt;
    }
}
