package com.funix.swp490x.mrs.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Addressing for the outbound Email Service (SRS 4.1).
 *
 * <p>Sits alongside Spring's own {@code spring.mail.*} settings, which describe
 * the SMTP connection; these describe the messages themselves.
 */
@ConfigurationProperties("mrs.mail")
public class MailProperties {

    private String from = "no-reply@mrs.local";

    private String fromName = "MRS";

    /**
     * Origin every link in a message is built against. A message is read
     * outside any request, so a relative path would have nothing to resolve
     * against.
     */
    private String baseUrl = "http://localhost:8080";

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
