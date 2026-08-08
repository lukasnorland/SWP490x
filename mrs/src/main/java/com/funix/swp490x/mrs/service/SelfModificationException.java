package com.funix.swp490x.mrs.service;

/**
 * ADMIN tried to deactivate or alter their own account from P-06a — the screen
 * must keep the signed-in operator able to finish the session.
 */
public class SelfModificationException extends RuntimeException {

    public SelfModificationException() {
        super("An ADMIN cannot deactivate or change the role of their own account.");
    }
}
