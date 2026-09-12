package com.funix.swp490x.mrs.security;

import com.funix.swp490x.mrs.service.SettingsService;
import com.funix.swp490x.mrs.settings.SettingKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies the P-06d session inactivity window to the current HTTP session.
 *
 * <p>{@code server.servlet.session.timeout} is fixed at boot; this overlay
 * lets ADMIN shorten or restore the window without a restart. New sessions
 * still pick up the container default until their first authenticated
 * request hits this filter.
 */
@Component
public class SessionTimeoutFilter extends OncePerRequestFilter {

    private final SettingsService settings;

    public SessionTimeoutFilter(ObjectProvider<SettingsService> settings) {
        this.settings = settings.getIfAvailable();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session != null) {
            int hours = settings != null
                    ? settings.sessionInactivityHours()
                    : SettingKey.SESSION_INACTIVITY_HOURS.defaultInt();
            session.setMaxInactiveInterval(hours * 3600);
        }
        filterChain.doFilter(request, response);
    }
}
