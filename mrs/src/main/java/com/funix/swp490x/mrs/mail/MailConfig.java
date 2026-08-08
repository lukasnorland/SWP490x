package com.funix.swp490x.mrs.mail;

import com.funix.swp490x.mrs.aws.AwsCredentialsFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    /**
     * Boot only auto-configures a {@link JavaMailSender} once
     * {@code spring.mail.host} is set, so its absence is the signal to fall
     * back to logging. Choosing here rather than failing startup keeps a fresh
     * checkout and the test suite runnable without a mail server.
     */
    @Bean
    public MailTransport mailTransport(ObjectProvider<JavaMailSender> mailSender,
            MailProperties properties) {

        JavaMailSender configured = mailSender.getIfAvailable();
        return configured == null
                ? new LoggingMailTransport()
                : new SmtpMailTransport(configured, properties);
    }

    /**
     * API client for declaring recipient identities from P-06a.
     *
     * <p>{@code mrs.mail.aws-profile} defaults to {@code mrs-admin}. That keeps
     * local runs on the personal SES account; clearing the property (or setting
     * it empty) falls back to the default credential chain, which is what EC2
     * needs for the instance role. Never the SMTP username/password in
     * {@code local.properties}.
     *
     * <p>{@link AwsCredentialsFactory} explains how a named profile is resolved.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(SesV2Client.class)
    public SesV2Client sesV2Client(MailProperties properties) {
        return SesV2Client.builder()
                .region(Region.of(properties.getSesRegion()))
                .credentialsProvider(credentialsFor(properties))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(SesIdentityService.class)
    public SesIdentityService sesIdentityService(SesV2Client sesV2Client) {
        return new AwsSesIdentityService(sesV2Client);
    }

    private static AwsCredentialsProvider credentialsFor(MailProperties properties) {
        return AwsCredentialsFactory.forProfile(properties.getAwsProfile(), "mrs.mail.aws-profile");
    }
}
