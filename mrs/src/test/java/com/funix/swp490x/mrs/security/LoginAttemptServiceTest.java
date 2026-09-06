package com.funix.swp490x.mrs.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    private static final String EMAIL = "nina@mrs.local";

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService();
    }

    @Test
    void isLocked_whenNoFailures_shouldReturnFalse() {
        assertThat(service.isLocked(EMAIL)).isFalse();
    }

    @Test
    void isLocked_whenEmailIsNull_shouldReturnFalse() {
        assertThat(service.isLocked(null)).isFalse();
    }

    @Test
    void recordFailure_whenFourthWithinWindow_shouldNotLock() {
        boolean locked = false;
        for (int i = 0; i < 4; i++) {
            locked = service.recordFailure(EMAIL);
        }

        assertThat(locked).isFalse();
        assertThat(service.isLocked(EMAIL)).isFalse();
    }

    @Test
    void recordFailure_whenFifthWithinWindow_shouldLock() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure(EMAIL);
        }

        assertThat(service.recordFailure(EMAIL)).isTrue();
        assertThat(service.isLocked(EMAIL)).isTrue();
        assertThat(service.lockRemaining(EMAIL)).isNotNull();
    }

    @Test
    void recordFailure_whenEmailCaseDiffers_shouldShareTheCounter() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure("Nina@MRS.local");
        }

        assertThat(service.recordFailure("nina@mrs.local")).isTrue();
        assertThat(service.isLocked("NINA@mrs.LOCAL")).isTrue();
    }

    @Test
    void recordFailure_whenEmailIsBlank_shouldIgnore() {
        assertThat(service.recordFailure("  ")).isFalse();
        assertThat(service.isLocked("  ")).isFalse();
        assertThat(service.recordFailure(EMAIL)).isFalse();
    }

    @Test
    void recordFailure_whenTwoThreadsHitTheLimit_shouldLockExactlyOnce() throws Exception {
        for (int i = 0; i < 4; i++) {
            service.recordFailure(EMAIL);
        }

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(2, TimeUnit.SECONDS);
                    return service.recordFailure(EMAIL);
                }));
            }
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Boolean> locked = new ArrayList<>();
            for (Future<Boolean> result : results) {
                locked.add(result.get(2, TimeUnit.SECONDS));
            }

            assertThat(service.isLocked(EMAIL)).isTrue();
            assertThat(locked).contains(true);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void reset_afterFailures_shouldClearTheCounter() {
        for (int i = 0; i < 3; i++) {
            service.recordFailure(EMAIL);
        }

        service.reset(EMAIL);

        assertThat(service.isLocked(EMAIL)).isFalse();
        for (int i = 0; i < 4; i++) {
            assertThat(service.recordFailure(EMAIL)).isFalse();
        }
        assertThat(service.recordFailure(EMAIL)).isTrue();
    }

    @Test
    void reset_whenNothingRecorded_shouldBeNoOp() {
        assertThatCode(() -> service.reset(EMAIL)).doesNotThrowAnyException();
        assertThat(service.isLocked(EMAIL)).isFalse();
    }
}
