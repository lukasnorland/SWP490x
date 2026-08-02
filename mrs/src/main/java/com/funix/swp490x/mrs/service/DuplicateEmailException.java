package com.funix.swp490x.mrs.service;

/** UC-07 E1 / DC-06: the address already belongs to an account. */
public class DuplicateEmailException extends RuntimeException {

    private final String email;

    public DuplicateEmailException(String email) {
        super("An account already exists for " + email);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
