package com.funix.swp490x.mrs.mail;

/**
 * The Email Service rejected or timed out on a message (UC-07 E3).
 *
 * <p>Unchecked because most senders cannot do anything useful about it. The one
 * caller that can — account creation, which must report MSG_022 without undoing
 * the account — catches it deliberately.
 */
public class MailDeliveryException extends RuntimeException {

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
