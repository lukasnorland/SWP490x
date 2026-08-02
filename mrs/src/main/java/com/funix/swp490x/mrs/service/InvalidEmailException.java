package com.funix.swp490x.mrs.service;

/**
 * The submitted address cannot reach a mailbox, so no account is created for
 * it (see {@link EmailPolicy}).
 */
public class InvalidEmailException extends RuntimeException {

    private final String email;

    public InvalidEmailException(String email) {
        super("Not a usable email address: " + email);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
