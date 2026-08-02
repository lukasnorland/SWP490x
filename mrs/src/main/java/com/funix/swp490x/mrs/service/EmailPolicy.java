package com.funix.swp490x.mrs.service;

import java.util.regex.Pattern;

/**
 * What counts as a usable address for an account.
 *
 * <p>The browser's {@code type="email"} field catches typos, but the server is
 * the boundary: an address that never reaches a mailbox produces an account
 * whose owner cannot sign in and cannot reset, because BR-15 delivers the only
 * password they are ever given by email.
 *
 * <p>Deliberately structural rather than exhaustive. No pattern can decide
 * whether a mailbox exists, so this rejects what is certainly wrong and leaves
 * the rest to delivery.
 */
public final class EmailPolicy {

    /** The {@code email} column of migration V1. */
    public static final int MAX_LENGTH = 255;

    /** One local part, one domain of at least two dot-separated labels. */
    private static final Pattern WELL_FORMED =
            Pattern.compile("^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+$");

    private EmailPolicy() {
    }

    public static boolean isWellFormed(String email) {
        return email != null
                && email.length() <= MAX_LENGTH
                && WELL_FORMED.matcher(email).matches();
    }
}
