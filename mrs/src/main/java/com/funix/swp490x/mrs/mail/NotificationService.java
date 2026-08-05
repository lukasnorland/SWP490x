package com.funix.swp490x.mrs.mail;

import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.security.PasswordResetTokenService;
import com.funix.swp490x.mrs.web.Routes;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Every message MRS sends (SRS 4.1, "outbound only").
 *
 * <p>Bodies come from templates under {@code templates/email} rendered without
 * a request, so they carry absolute links and cannot use {@code @{...}} URL
 * expressions.
 */
@Service
public class NotificationService {

    private final MailTransport transport;
    private final TemplateEngine templateEngine;
    private final MailProperties properties;

    public NotificationService(MailTransport transport, TemplateEngine templateEngine,
            MailProperties properties) {
        this.transport = transport;
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    /** UC-02: the time-limited link behind P-01. */
    public void sendPasswordResetLink(String email, String token) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("resetUrl", absolute(Routes.PASSWORD_RESET_SET + "?token=" + token));
        context.setVariable("validityMinutes", PasswordResetTokenService.VALIDITY.toMinutes());

        transport.send(email, "Reset your MRS password",
                templateEngine.process("email/password-reset", context));
    }

    /**
     * BR-15: the login email and initial password for an account ADMIN just
     * created. The password travels in plain text and stays readable in the
     * recipient's mailbox afterwards, which the forced change at first login
     * limits but does not remove (UC-07, Other Information).
     */
    public void sendAccountCredentials(User user, String initialPassword) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("name", user.getUsername());
        context.setVariable("email", user.getEmail());
        context.setVariable("password", initialPassword);
        context.setVariable("role", user.getRole().getDisplayName());
        context.setVariable("loginUrl", absolute(Routes.LOGIN));

        transport.send(user.getEmail(), "Your MRS account",
                templateEngine.process("email/account-credentials", context));
    }

    /**
     * Public registration intent from the login landing page.
     *
     * <p>The notification goes to the configured main mailbox ({@code mrs.mail.from}),
     * which is the ADMIN-maintained email identity already used for outbound credentials.
     */
    public void sendRegistrationRequest(String requesterEmail) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("email", requesterEmail);
        context.setVariable("loginUrl", absolute(Routes.LOGIN));

        transport.send(properties.getFrom(), "New MRS registration request",
                templateEngine.process("email/register-request", context));
    }

    private String absolute(String path) {
        String base = properties.getBaseUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) + path : base + path;
    }
}
