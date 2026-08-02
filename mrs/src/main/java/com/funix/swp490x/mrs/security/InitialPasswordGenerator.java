package com.funix.swp490x.mrs.security;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds an initial password that satisfies {@link PasswordPolicy} by
 * construction, so the generate button on P-06a can never hand ADMIN something
 * the server will then reject.
 *
 * <p>The alphabets leave out characters that are easy to confuse — {@code I l 1
 * O 0} — because this password is read out of an email and typed by hand once.
 */
public final class InitialPasswordGenerator {

    private static final String UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIALS = "!@#$%^&*";

    private static final int LENGTH = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    private InitialPasswordGenerator() {
    }

    public static String generate() {
        List<Character> characters = new ArrayList<>(LENGTH);
        characters.add(pick(UPPERCASE));
        characters.add(pick(LOWERCASE));
        characters.add(pick(DIGITS));
        characters.add(pick(SPECIALS));

        String everything = UPPERCASE + LOWERCASE + DIGITS + SPECIALS;
        while (characters.size() < LENGTH) {
            characters.add(pick(everything));
        }

        // Otherwise the first four positions would always be the same classes.
        Collections.shuffle(characters, RANDOM);

        StringBuilder password = new StringBuilder(LENGTH);
        characters.forEach(password::append);
        return password.toString();
    }

    private static char pick(String alphabet) {
        return alphabet.charAt(RANDOM.nextInt(alphabet.length()));
    }
}
