package com.funix.swp490x.mrs.security;

import com.funix.swp490x.mrs.web.Routes;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Sends each role to its own landing page (spec 2.1), except when the account
 * still owes a password change, which takes precedence (FT-09).
 */
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final LoginAttemptService loginAttemptService;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    public LoginSuccessHandler(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        String target = Routes.SEARCH;
        if (authentication.getPrincipal() instanceof MrsUserDetails user) {
            loginAttemptService.reset(user.getEmail());
            target = user.isMustChangePassword()
                    ? Routes.PASSWORD_CHANGE
                    : user.getRole().getLandingPath();
        }
        redirectStrategy.sendRedirect(request, response, target);
    }
}
