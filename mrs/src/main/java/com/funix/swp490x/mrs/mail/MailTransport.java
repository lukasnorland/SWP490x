package com.funix.swp490x.mrs.mail;

/**
 * Delivery of one already-rendered message.
 *
 * <p>Separate from {@link NotificationService} so that what MRS says is decided
 * in one place and how it leaves the building in another: with no SMTP host
 * configured the messages are still composed, just logged instead of sent.
 */
public interface MailTransport {

    /**
     * @throws MailDeliveryException when the message could not be handed over
     */
    void send(String to, String subject, String htmlBody);
}
