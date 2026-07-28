package com.funix.swp490x.mrs.security;

import com.funix.swp490x.mrs.web.Routes;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
 * (spec 4.1).
 */
@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    public LoginFailureHandler(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {

        String email = request.getParameter("email");
        boolean locked = exception instanceof LockedException || loginAttemptService.recordFailure(email);

        redirectStrategy.sendRedirect(request, response,
                locked ? Routes.LOGIN + "?locked" : Routes.LOGIN + "?error");
    }
}
