package com.funix.swp490x.mrs.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stand-in used when no SMTP host is configured.
 *
 * <p>Writes the whole message to the log so a local run or the demo can still
 * follow a reset link and read an initial password. Every flow behaves exactly
 * as it would with a real server, including never failing, which is why this is
 * a development aid rather than something to leave on in production.
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
