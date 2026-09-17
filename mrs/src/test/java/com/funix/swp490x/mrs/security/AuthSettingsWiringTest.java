package com.funix.swp490x.mrs.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.funix.swp490x.mrs.service.SettingsService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class AuthSettingsWiringTest {
    @Test
    void lockAndResetExpireAtConfiguredBoundaries() {
        SettingsService settings = mock(SettingsService.class);
        when(settings.lockoutThreshold()).thenReturn(5);
        when(settings.lockoutWindow()).thenReturn(Duration.ofMinutes(1));
        when(settings.resetLinkValidity()).thenReturn(Duration.ofMinutes(5));
        java.time.Clock clock = mock(java.time.Clock.class);
        var issuedAt = java.time.Instant.parse("2026-09-18T00:00:00Z");
        when(clock.instant()).thenReturn(issuedAt);
        var login = new LoginAttemptService(settings, clock);
        var reset = new PasswordResetTokenService(settings, clock);
        for (int i = 0; i < 5; i++) login.recordFailure("user@example.invalid");
        String token = reset.issue("user@example.invalid");
        when(clock.instant()).thenReturn(issuedAt.plusSeconds(59));
        assertThat(login.isLocked("user@example.invalid")).isTrue();
        when(clock.instant()).thenReturn(issuedAt.plusSeconds(60));
        assertThat(login.isLocked("user@example.invalid")).isFalse();
        assertThat(login.recordFailure("user@example.invalid")).isFalse();
        when(clock.instant()).thenReturn(issuedAt.plusSeconds(299));
        assertThat(reset.emailFor(token)).contains("user@example.invalid");
        when(clock.instant()).thenReturn(issuedAt.plusSeconds(300));
        assertThat(reset.emailFor(token)).isEmpty();
        assertThat(reset.emailFor(reset.issue("user@example.invalid"))).contains("user@example.invalid");
    }

    @Test
    void springBeansUseSettingsAndSeeSubsequentChanges() {
        SettingsService settings = mock(SettingsService.class);
        when(settings.lockoutThreshold()).thenReturn(2);
        when(settings.lockoutWindow()).thenReturn(Duration.ofMinutes(1));
        when(settings.resetLinkValidity()).thenReturn(Duration.ofMinutes(5));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SettingsService.class, () -> settings);
            context.register(LoginAttemptService.class, PasswordResetTokenService.class);
            context.refresh();
            var login = context.getBean(LoginAttemptService.class);
            var reset = context.getBean(PasswordResetTokenService.class);
            assertThat(login.recordFailure("one@example.invalid")).isFalse();
            assertThat(login.recordFailure("one@example.invalid")).isTrue();
            assertThat(login.lockRemaining("one@example.invalid"))
                    .isBetween(Duration.ofSeconds(55), Duration.ofSeconds(60));
            assertThat(reset.validity()).isEqualTo(Duration.ofMinutes(5));

            when(settings.lockoutWindow()).thenReturn(Duration.ofMinutes(2));
            when(settings.resetLinkValidity()).thenReturn(Duration.ofMinutes(10));
            login.recordFailure("two@example.invalid");
            login.recordFailure("two@example.invalid");
            assertThat(login.lockRemaining("two@example.invalid"))
                    .isBetween(Duration.ofSeconds(115), Duration.ofSeconds(120));
            assertThat(reset.validity()).isEqualTo(Duration.ofMinutes(10));
        }
    }
}
