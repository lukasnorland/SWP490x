package com.funix.swp490x.mrs.service;

/**
 * ADMIN tried to deactivate or alter their own account from P-06a — the screen
 * must keep the signed-in operator able to finish the session.
 */
public class SelfModificationException extends RuntimeException {

    public SelfModificationException() {
        super("An ADMIN cannot deactivate, change the role of, or resend credentials "
                + "for their own account.");
    }
}
