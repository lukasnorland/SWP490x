package com.funix.swp490x.mrs.web.support;

import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.web.Routes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Holds an account on the change-password screen until it has set its own
 * password (FT-09, flow F-04). The login redirect alone is not enough — the
 * user could navigate away — so every request is checked.
 */
@Component
public class ForcedPasswordChangeInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof MrsUserDetails user
                && user.isMustChangePassword()) {
            response.sendRedirect(request.getContextPath() + Routes.PASSWORD_CHANGE);
            return false;
        }
        return true;
    }
}
