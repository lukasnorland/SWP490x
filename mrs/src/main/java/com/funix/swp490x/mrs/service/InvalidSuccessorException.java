package com.funix.swp490x.mrs.service;

/** A successor on deactivation or demotion is missing or not allowed. */
public class InvalidSuccessorException extends RuntimeException {

    public InvalidSuccessorException(String message) {
        super(message);
    }
}
