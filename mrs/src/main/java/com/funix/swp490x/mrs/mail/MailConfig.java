package com.funix.swp490x.mrs.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProcessCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    private static final Pattern SAFE_PROFILE = Pattern.compile("^[A-Za-z0-9._-]+$");

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
     * <p>A named profile is resolved through {@code aws configure
     * export-credentials}, not {@link software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider}.
     * {@code aws login} stores a {@code login_session} that the profile provider
     * cannot read. On Windows the export is launched via {@code cmd /c} so the
     * CLI is found on PATH — ProcessBuilder cannot run {@code aws.cmd} directly.
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
        if (!StringUtils.hasText(properties.getAwsProfile())) {
            return DefaultCredentialsProvider.create();
        }

        String profile = properties.getAwsProfile().trim();
        if (!SAFE_PROFILE.matcher(profile).matches()) {
            throw new IllegalArgumentException("Invalid mrs.mail.aws-profile: " + profile);
        }

        return ProcessCredentialsProvider.builder()
                .command(exportCredentialsCommand(profile))
                .build();
    }

    private static List<String> exportCredentialsCommand(String profile) {
        List<String> command = new ArrayList<>();
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add("aws");
        command.add("configure");
        command.add("export-credentials");
        command.add("--profile");
        command.add(profile);
        command.add("--format");
        command.add("process");
        return command;
    }
}
