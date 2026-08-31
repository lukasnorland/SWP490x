package com.funix.swp490x.mrs.service;

/**
 * The contextual query is outside the 10–200 character FT-04 window.
 */
public class InvalidSearchQueryException extends RuntimeException {

    public InvalidSearchQueryException(String message) {
        super(message);
    }
}
