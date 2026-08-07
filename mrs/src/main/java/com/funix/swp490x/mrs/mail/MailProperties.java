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

    /**
     * Region the SES v2 API client talks to when ADMIN prepares a recipient
     * identity from P-06a. Must match the region of {@code spring.mail.host}
     * and of every identity already verified there — SES does not share
     * identities across regions.
     */
    private String sesRegion = "ap-southeast-1";

    /**
     * Named profile for the SES API client. Defaults to {@code mrs-admin} so a
     * developer checkout never falls through to an unrelated AWS account.
     * Clear it (empty string) on EC2 so the instance role is used instead.
     */
    private String awsProfile = "mrs-admin";

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

    public String getSesRegion() {
        return sesRegion;
    }

    public void setSesRegion(String sesRegion) {
        this.sesRegion = sesRegion;
    }

    public String getAwsProfile() {
        return awsProfile;
    }

    public void setAwsProfile(String awsProfile) {
        this.awsProfile = awsProfile;
    }
}
