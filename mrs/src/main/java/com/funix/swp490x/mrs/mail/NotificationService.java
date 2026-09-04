package com.funix.swp490x.mrs.mail;

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
    public void sendAccountCredentials(String name, String email, String roleDisplayName,
            String initialPassword) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("name", name);
        context.setVariable("email", email);
        context.setVariable("password", initialPassword);
        context.setVariable("role", roleDisplayName);
        context.setVariable("loginUrl", absolute(Routes.LOGIN));

        transport.send(email, "Your MRS account",
                templateEngine.process("email/account-credentials", context));
    }

    /**
     * Spec 4.9: the account holder learns that ADMIN deactivated the account.
     * Sent after the soft-delete committed, so a failed delivery never leaves
     * the account active. {@code transferredPlaylists} is how many of their
     * playlists went to an administrator; zero hides that paragraph.
     */
    public void sendAccountDeactivated(String name, String email, int transferredPlaylists) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("name", name);
        context.setVariable("email", email);
        context.setVariable("transferredPlaylists", transferredPlaylists);

        transport.send(email, "Your MRS account has been deactivated",
                templateEngine.process("email/account-deactivated", context));
    }

    /**
     * UC-06: the account holder learns which role ADMIN moved them from and to.
     * Their sessions are already gone by the time this goes out, so the message
     * also says to sign in again. {@code transferredPlaylists} is how many of
     * their playlists went to an administrator with the demotion; zero hides
     * that paragraph.
     */
    public void sendRoleChanged(String name, String email, String previousRoleDisplayName,
            String newRoleDisplayName, int transferredPlaylists) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("name", name);
        context.setVariable("previousRole", previousRoleDisplayName);
        context.setVariable("newRole", newRoleDisplayName);
        context.setVariable("transferredPlaylists", transferredPlaylists);
        context.setVariable("loginUrl", absolute(Routes.LOGIN));

        transport.send(email, "Your MRS role has changed",
                templateEngine.process("email/role-changed", context));
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
