package com.funix.swp490x.mrs.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Outbound message settings, separate from the SMTP connection configuration. */
@ConfigurationProperties("mrs.mail")
public class MailProperties {

    private String from = "no-reply@mrs.local";

    private String fromName = "MRS";

    /** Absolute base URL for links in outbound mail. */
    private String baseUrl = "http://localhost:8080";

    /** SES API region; must match the SMTP endpoint and verified identities. */
    private String sesRegion = "ap-southeast-1";

    /** CLI profile for SES; blank uses the default credential chain, including the EC2 role. */
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
