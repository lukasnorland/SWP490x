package com.funix.swp490x.mrs.mail;

import com.funix.swp490x.mrs.security.PasswordResetTokenService;
import com.funix.swp490x.mrs.web.Routes;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/** Renders outbound email templates without a request; links use an absolute base URL. */
@Service
public class NotificationService {

    private final MailTransport transport;
    private final TemplateEngine templateEngine;
    private final MailProperties properties;
    private final PasswordResetTokenService tokens;

    public NotificationService(MailTransport transport, TemplateEngine templateEngine,
            MailProperties properties, PasswordResetTokenService tokens) {
        this.transport = transport;
        this.templateEngine = templateEngine;
        this.properties = properties;
        this.tokens = tokens;
    }

    /** UC-02: the time-limited link behind P-01. */
    public void sendPasswordResetLink(String email, String token) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("resetUrl", absolute(Routes.PASSWORD_RESET_SET + "?token=" + token));
        context.setVariable("validityMinutes", tokens.validity().toMinutes());

        transport.send(email, "Reset your MRS password",
                templateEngine.process("email/password-reset", context));
    }

    /**
     * Emails an ADMIN-created account its login address and initial password (BR-15).
     * The account must change that password at first login.
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

    /** Notifies the holder after account deactivation commits; includes any playlist succession count. */
    public void sendAccountDeactivated(String name, String email, int transferredPlaylists) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("name", name);
        context.setVariable("email", email);
        context.setVariable("transferredPlaylists", transferredPlaylists);

        transport.send(email, "Your MRS account has been deactivated",
                templateEngine.process("email/account-deactivated", context));
    }

    /** Notifies the holder after a role change and session revocation; includes any succession count. */
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
     * Notifies {@code recipient} after an ADMIN edit of name, email or role.
     * Each {@code previous*} argument is null when that field did not change,
     * so the message lists only what moved. The password is never included.
     */
    public void sendAccountUpdated(String recipient, String name, String previousName,
            String email, String previousEmail, String roleDisplayName,
            String previousRoleDisplayName, int transferredPlaylists) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("name", name);
        context.setVariable("previousName", previousName);
        context.setVariable("email", email);
        context.setVariable("previousEmail", previousEmail);
        context.setVariable("role", roleDisplayName);
        context.setVariable("previousRole", previousRoleDisplayName);
        context.setVariable("transferredPlaylists", transferredPlaylists);
        context.setVariable("loginUrl", absolute(Routes.LOGIN));

        transport.send(recipient, "Your MRS account details have changed",
                templateEngine.process("email/account-updated", context));
    }

    /** Sends an account-request notice to the configured ADMIN mailbox; creates no account (UC-38). */
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
