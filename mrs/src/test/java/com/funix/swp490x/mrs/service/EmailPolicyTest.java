package com.funix.swp490x.mrs.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "admin@mrs.local",
        "nina.designer@mrs.local",
        "nina+curation@mrs.co.uk",
        "n@a.io"
    })
    void ordinaryInternalAddressesAreAccepted(String email) {
        assertThat(EmailPolicy.isWellFormed(email)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "nina",
        "nina@",
        "@mrs.local",
        "nina@mrs",
        "nina@@mrs.local",
        "nina designer@mrs.local",
        "nina@mrs .local",
        "nina@mrs..local",
        "nina@.local"
    })
    void addressesThatCannotReachAMailboxAreRejected(String email) {
        assertThat(EmailPolicy.isWellFormed(email)).isFalse();
    }

    /** Anything longer would be truncated by the column and stop matching. */
    @Test
    void addressesLongerThanTheColumnAreRejected() {
        String domain = "@mrs.local";
        String tooLong = "n".repeat(EmailPolicy.MAX_LENGTH - domain.length() + 1) + domain;

        assertThat(tooLong).hasSizeGreaterThan(EmailPolicy.MAX_LENGTH);
        assertThat(EmailPolicy.isWellFormed(tooLong)).isFalse();
    }

    @Test
    void isWellFormed_whenLengthIsExactlyTheColumnLimit_shouldAccept() {
        String domain = "@mrs.local";
        String exact = "n".repeat(EmailPolicy.MAX_LENGTH - domain.length()) + domain;

        assertThat(exact).hasSize(EmailPolicy.MAX_LENGTH);
        assertThat(EmailPolicy.isWellFormed(exact)).isTrue();
    }
}
