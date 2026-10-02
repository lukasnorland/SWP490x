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

    /** Uses SMTP when {@code JavaMailSender} is configured, otherwise logs messages. */
    @Bean
    public MailTransport mailTransport(ObjectProvider<JavaMailSender> mailSender,
            MailProperties properties) {

        JavaMailSender configured = mailSender.getIfAvailable();
        return configured == null
                ? new LoggingMailTransport()
                : new SmtpMailTransport(configured, properties);
    }

    /**
     * SES recipient-identity client; uses AWS API credentials, not SMTP credentials.
     * A blank profile selects the default credential chain for the EC2 role.
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
