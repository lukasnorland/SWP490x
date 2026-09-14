package com.funix.swp490x.mrs.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequestRateLimiterTest {

    private static final String KEY = "203.0.113.7";

    private TickingClock clock;
    private RequestRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new TickingClock();
        limiter = new RequestRateLimiter(null, clock);
    }

    @Test
    void tryAcquire_whenThirdWithinTheWindow_shouldAllow() {
        assertThat(limiter.tryAcquire(KEY)).isTrue();
        assertThat(limiter.tryAcquire(KEY)).isTrue();
        assertThat(limiter.tryAcquire(KEY)).isTrue();
    }

    /** BV-12: three per hour, so the fourth is the first refusal. */
    @Test
    void tryAcquire_whenFourthWithinTheWindow_shouldRefuse() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(KEY);
        }

        assertThat(limiter.tryAcquire(KEY)).isFalse();
    }

    @Test
    void tryAcquire_whenStillInsideTheWindow_shouldStayRefused() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire(KEY);
        }

        clock.advance(Duration.ofMinutes(59));

        assertThat(limiter.tryAcquire(KEY)).isFalse();
    }

    @Test
    void tryAcquire_whenTheWindowHasPassed_shouldAllowAgain() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire(KEY);
        }

        clock.advance(Duration.ofMinutes(60));

        assertThat(limiter.tryAcquire(KEY)).isTrue();
    }

    @Test
    void tryAcquire_whenKeysDiffer_shouldCountSeparately() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire(KEY);
        }

        assertThat(limiter.tryAcquire("198.51.100.4")).isTrue();
    }

    /** A caller with no usable address must not get an uncapped bucket. */
    @Test
    void tryAcquire_whenKeyIsBlank_shouldStillCap() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("  ")).isTrue();
        }

        assertThat(limiter.tryAcquire(null)).isFalse();
    }

    private static final class TickingClock extends Clock {

        private Instant now = Instant.parse("2026-09-14T12:00:00Z");

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
