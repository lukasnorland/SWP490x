package com.funix.swp490x.mrs.security;

import java.util.ArrayList;
import java.util.List;

/**
 * BR-12 / BV-02 password rules: 8–64 characters with at least one uppercase
 * letter, one digit and one special character.
 *
 * <p>The same four rules drive the live checklist on P-01, P-05 and P-06a. The
 * checklist in {@code mrs.js} mirrors them for instant feedback; this class is
 * the authority, because the server is the boundary.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;

    private PasswordPolicy() {
    }

    /**
     * Returns the unmet rules, named as the UI names them, so a rejection can
     * point at the specific rule that failed (FT-01 NAC-04).
     */
    public static List<String> violations(String password) {
        List<String> violations = new ArrayList<>();
        String candidate = password == null ? "" : password;

        if (candidate.length() < MIN_LENGTH || candidate.length() > MAX_LENGTH) {
            violations.add("Use between %d and %d characters".formatted(MIN_LENGTH, MAX_LENGTH));
        }
        if (!candidate.matches(".*[A-Z].*")) {
            violations.add("Include an uppercase letter");
        }
        if (!candidate.matches(".*[0-9].*")) {
            violations.add("Include a digit");
        }
        if (!candidate.matches(".*[^A-Za-z0-9].*")) {
            violations.add("Include a special character");
        }
        return violations;
    }

    public static boolean isValid(String password) {
        return violations(password).isEmpty();
    }
}
