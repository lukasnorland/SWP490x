package com.funix.swp490x.mrs.service;

/** No account exists for the requested id. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long id) {
        super("No account with id " + id);
    }
}
