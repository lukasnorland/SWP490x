package com.funix.swp490x.mrs.service;

/** The submitted account name is blank or longer than the {@code users.username} column. */
public class InvalidNameException extends RuntimeException {

    public InvalidNameException(String name) {
        super("Not a usable account name: " + name);
    }
}
