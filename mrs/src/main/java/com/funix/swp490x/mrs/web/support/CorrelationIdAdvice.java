package com.funix.swp490x.mrs.web.support;

import com.funix.swp490x.mrs.security.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the request id to templates. Thymeleaf 3.1 no longer provides
 * {@code #request}, and the error dispatch skips {@link CorrelationIdFilter},
 * so the 500 page reads this attribute rather than the servlet API.
 */
@ControllerAdvice
public class CorrelationIdAdvice {

    @ModelAttribute(CorrelationIdFilter.MDC_KEY)
    public String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.MDC_KEY);
        return value instanceof String id ? id : null;
    }
}
