package com.funix.swp490x.mrs.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

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
}
