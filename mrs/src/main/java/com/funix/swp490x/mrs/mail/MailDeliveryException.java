package com.funix.swp490x.mrs.mail;

/** Outbound mail failed. Account creation reports the failure after preserving the account (UC-07 E3). */
public class MailDeliveryException extends RuntimeException {

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
