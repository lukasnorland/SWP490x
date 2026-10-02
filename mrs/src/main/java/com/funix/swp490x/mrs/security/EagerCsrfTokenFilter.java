package com.funix.swp490x.mrs.security;

import com.funix.swp490x.mrs.web.Routes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the deferred CSRF token before rendering, so any session cookie is set
 * before the response commits. Static assets are excluded.
 */
public class EagerCsrfTokenFilter extends OncePerRequestFilter {

    private final AntPathMatcher matcher = new AntPathMatcher();

    /** Assets never render a form, so priming a session for each would be waste. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return Arrays.stream(Routes.STATIC_ASSETS)
                .anyMatch(pattern -> matcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            // Deferred until read; reading it here writes the session cookie
            // while the response can still carry headers.
            token.getToken();
        }

        chain.doFilter(request, response);
    }
}
