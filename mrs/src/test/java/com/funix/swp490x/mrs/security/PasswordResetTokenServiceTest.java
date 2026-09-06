package com.funix.swp490x.mrs.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PasswordResetTokenServiceTest {

    private static final String EMAIL = "nina@mrs.local";

    private PasswordResetTokenService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetTokenService();
    }

    @Test
    void issue_shouldBindTokenToEmail() {
        String token = service.issue(EMAIL);

        assertThat(service.emailFor(token)).contains(EMAIL);
    }

    @Test
    void issue_twiceForSameEmail_shouldReturnDistinctTokens() {
        String first = service.issue(EMAIL);
        String second = service.issue(EMAIL);

        assertThat(first).isNotEqualTo(second);
        assertThat(service.emailFor(first)).contains(EMAIL);
        assertThat(service.emailFor(second)).contains(EMAIL);
    }

    @Test
    void issue_shouldProduceUrlSafeToken() {
        String token = service.issue(EMAIL);

        assertThat(token).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void emailFor_whenTokenUnknownOrNull_shouldBeEmpty() {
        assertThat(service.emailFor("unknown")).isEmpty();
        assertThat(service.emailFor(null)).isEqualTo(Optional.empty());
    }

    @Test
    void invalidate_shouldMakeTokenSingleUse() {
        String token = service.issue(EMAIL);

        service.invalidate(token);

        assertThat(service.emailFor(token)).isEmpty();
    }

    @Test
    void invalidate_whenTokenUnknownOrNull_shouldBeNoOp() {
        assertThatCode(() -> {
            service.invalidate(null);
            service.invalidate("unknown");
        }).doesNotThrowAnyException();
    }
}
