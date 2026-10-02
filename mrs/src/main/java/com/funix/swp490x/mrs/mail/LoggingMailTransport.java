package com.funix.swp490x.mrs.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs rendered messages for development when no SMTP host is configured.
 * Does not deliver mail or simulate SMTP failures.
 */
class LoggingMailTransport implements MailTransport {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailTransport.class);

    @Override
    public void send(String to, String subject, String htmlBody) {
        log.info("No SMTP host configured, so this message was not sent.\n  To: {}\n  Subject: {}\n  {}",
                to, subject, asReadableText(htmlBody));
    }

    /** Markup would bury the link and the password the reader is here for. */
    private static String asReadableText(String htmlBody) {
        return htmlBody.replaceAll("(?s)<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }
}
