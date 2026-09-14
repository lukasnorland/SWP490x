package com.funix.swp490x.mrs.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stamps every request with {@code X-Request-Id} and puts the same value on
 * the MDC so log lines from one call can be grepped together.
 *
 * <p>The id also lives as a request attribute: {@code OncePerRequestFilter}
 * skips the error dispatch, and the MDC is cleared in {@code finally}, but
 * Spring Boot's {@code BasicErrorController} forward keeps attributes, so
 * {@code error/500.html} can print it as an opaque reference (SYS_001).
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String id = StringUtils.hasText(incoming) ? incoming.trim() : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, id);
        request.setAttribute(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
