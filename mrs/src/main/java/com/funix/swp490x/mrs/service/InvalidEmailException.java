package com.funix.swp490x.mrs.service;

/** The submitted address fails {@link EmailPolicy} syntax or length validation. */
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
