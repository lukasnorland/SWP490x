package com.funix.swp490x.mrs.service;

import java.util.regex.Pattern;

/** Validates account email syntax and column length; does not verify mailbox existence. */
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
