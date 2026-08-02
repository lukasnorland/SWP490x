package com.funix.swp490x.mrs.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * The generated password is emailed straight to a new user (BR-15), so a run
 * that happened to miss a character class would lock that account out behind a
 * server-side rejection nobody sees.
 */
class InitialPasswordGeneratorTest {

    private static final int SAMPLES = 500;

    @Test
    void everyGeneratedPasswordSatisfiesThePolicy() {
        IntStream.range(0, SAMPLES).forEach(i ->
                assertThat(PasswordPolicy.violations(InitialPasswordGenerator.generate()))
                        .as("attempt %d", i)
                        .isEmpty());
    }

    @Test
    void passwordsAreNotRepeated() {
        Set<String> generated = new HashSet<>();
        IntStream.range(0, SAMPLES).forEach(i -> generated.add(InitialPasswordGenerator.generate()));

        assertThat(generated).hasSize(SAMPLES);
    }

    /** Read off a screen and typed by hand once, so no I/l/1 or O/0. */
    @Test
    void easilyConfusedCharactersAreLeftOut() {
        IntStream.range(0, SAMPLES).forEach(i ->
                assertThat(InitialPasswordGenerator.generate()).doesNotContainAnyWhitespaces()
                        .doesNotContain("I", "l", "1", "O", "0"));
    }
}
