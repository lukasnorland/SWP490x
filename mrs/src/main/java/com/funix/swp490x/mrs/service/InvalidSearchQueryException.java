package com.funix.swp490x.mrs.service;

/** The contextual query is outside the configured length range; default 10-200 characters (FT-04). */
public class InvalidSearchQueryException extends RuntimeException {

    public InvalidSearchQueryException(String message) {
        super(message);
    }
}
