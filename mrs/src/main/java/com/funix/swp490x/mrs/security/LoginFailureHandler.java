package com.funix.swp490x.mrs.security;

import com.funix.swp490x.mrs.domain.AuditLog;
import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.web.Routes;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Counts failed attempts and picks which P-00 banner to show.
 *
 * <p>Every non-lockout failure lands on the same generic banner, so the screen
 * never reveals whether an email is registered or an account is deactivated
 * (spec 4.1). When the address is registered, a {@code LOGIN_FAILED} audit
 * row is stored against that account (actor_id stays NOT NULL).
 */
@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;
    private final ObjectProvider<UserRepository> users;
    private final ObjectProvider<AuditLogRepository> auditLogs;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    public LoginFailureHandler(LoginAttemptService loginAttemptService,
            ObjectProvider<UserRepository> users,
            ObjectProvider<AuditLogRepository> auditLogs) {
        this.loginAttemptService = loginAttemptService;
        this.users = users;
        this.auditLogs = auditLogs;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {

        String email = request.getParameter("email");
        boolean locked = exception instanceof LockedException || loginAttemptService.recordFailure(email);
        auditFailedLogin(email);

        redirectStrategy.sendRedirect(request, response,
                locked ? Routes.LOGIN + "?locked" : Routes.LOGIN + "?error");
    }

    private void auditFailedLogin(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        UserRepository userRepository = users.getIfAvailable();
        AuditLogRepository logs = auditLogs.getIfAvailable();
        if (userRepository == null || logs == null) {
            return;
        }
        userRepository.findByEmail(email.trim()).ifPresent(user ->
                logs.save(new AuditLog(user.getId(), AuditLog.ACTION_LOGIN_FAILED,
                        AuditLog.ENTITY_USER, user.getId(), null)));
    }
}
