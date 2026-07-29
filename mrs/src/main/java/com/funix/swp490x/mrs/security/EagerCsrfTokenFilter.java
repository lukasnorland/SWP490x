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
 * Materialises the CSRF token before rendering begins.
 *
 * <p>Spring Security defers the token: it is only generated when something asks
 * for it, and generating it creates the HTTP session that stores it. Thymeleaf
 * asks while processing {@code th:action}, which sits partway down a page. Any
 * page whose earlier markup exceeds the container's response buffer — the icon
 * sprite alone is several kilobytes — has already been flushed by then, so the
 * response is committed, no {@code Set-Cookie} can be added, and session
 * creation fails with {@code IllegalStateException}. The container has already
 * sent HTTP 200, so the browser receives a truncated page: on P-00 the heading
 * and banner arrive and the credential form does not.
 *
 * <p>Touching the token here, before the servlet writes anything, moves session
 * creation to a point where the response is still uncommitted. It also keeps the
 * page's byte size from being something templates have to stay under.
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
