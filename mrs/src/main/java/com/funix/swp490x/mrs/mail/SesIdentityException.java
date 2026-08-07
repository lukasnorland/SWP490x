package com.funix.swp490x.mrs.mail;

/**
 * Raised when Amazon SES refuses or cannot complete an identity operation
 * started from P-06a. Callers translate it into a screen message rather than
 * leaking SDK wording.
 */
public class SesIdentityException extends RuntimeException {

    public SesIdentityException(String message, Throwable cause) {
        super(message, cause);
    }

    public SesIdentityException(String message) {
        super(message);
    }
}
