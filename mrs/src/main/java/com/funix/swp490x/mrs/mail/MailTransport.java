package com.funix.swp490x.mrs.mail;

/** Delivers an already-rendered message; implementations use SMTP or development logging. */
public interface MailTransport {

    /**
     * @throws MailDeliveryException when the message could not be handed over
     */
    void send(String to, String subject, String htmlBody);
}
