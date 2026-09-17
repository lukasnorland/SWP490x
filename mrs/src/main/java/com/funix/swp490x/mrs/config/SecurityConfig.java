package com.funix.swp490x.mrs.config;

import com.funix.swp490x.mrs.domain.AuditLog;
import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.security.CorrelationIdFilter;
import com.funix.swp490x.mrs.security.EagerCsrfTokenFilter;
import com.funix.swp490x.mrs.security.LoginFailureHandler;
import com.funix.swp490x.mrs.security.LoginSuccessHandler;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.web.Routes;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Authentication and role-based access.
 *
 * <p>This is the real security boundary (NFR-SEC03). The sidebar hides sections
 * a role cannot use, but that is a usability measure only — every rule below is
 * enforced server-side regardless of what the UI renders.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Bootstrap's JS sets inline widths on progress bars and modals, so
     * {@code style-src} keeps {@code 'unsafe-inline'}. Scripts and fonts are
     * self-hosted.
     */
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: https:",
            "media-src 'self' https: blob:",
            "font-src 'self'",
            "connect-src 'self'",
            "frame-ancestors 'none'",
            "base-uri 'self'",
            "form-action 'self'");

    /** TDS 5.5 — the application uses none of these. */
    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=()";

    /** BCrypt only (NFR-SEC02); cost 12. Seeded V2 hashes are {@code $2a$10$}
     *  and still verify via {@code matches}. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Tracks signed-in principals so P-06a can expire sessions when an account
     * is deactivated (FT-01 AC-03 / spec 4.9).
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * Publishes create/destroy events into {@link SessionRegistry}. Without it
     * the registry never learns about sessions that end outside Spring Security.
     */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler(ObjectProvider<AuditLogRepository> auditLogs) {
        return (request, response, accessDeniedException) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            AuditLogRepository logs = auditLogs.getIfAvailable();
            if (logs != null && auth != null && auth.getPrincipal() instanceof MrsUserDetails user) {
                String path = request.getRequestURI() == null ? "" : request.getRequestURI();
                String safe = path.replace("\\", "\\\\").replace("\"", "\\\"");
                logs.save(new AuditLog(user.getId(), AuditLog.ACTION_ACCESS_DENIED,
                        AuditLog.ENTITY_REQUEST, 0L, "{\"path\":\"" + safe + "\"}"));
            }
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            LoginSuccessHandler successHandler, LoginFailureHandler failureHandler,
            SessionRegistry sessionRegistry, AccessDeniedHandler accessDeniedHandler)
            throws Exception {

        http
                .addFilterBefore(new CorrelationIdFilter(), CsrfFilter.class)
                // CsrfFilter publishes the deferred token; this reads it while the
                // response is still uncommitted, so no template can trigger session
                // creation mid-render.
                .addFilterAfter(new EagerCsrfTokenFilter(), CsrfFilter.class)
                // nosniff, DENY and HSTS come from the Spring Security defaults;
                // HSTS only goes out on a secure request, so it appears once TLS
                // terminates at the application rather than at a plain-HTTP proxy.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer ->
                                referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(permissions ->
                                permissions.policy(PERMISSIONS_POLICY)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(Routes.STATIC_ASSETS)
                        .permitAll()
                        .requestMatchers(Routes.LOGIN, Routes.REGISTER_REQUEST,
                                Routes.PASSWORD_RESET, Routes.PASSWORD_RESET + "/**")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/actuator/**")
                        .hasRole("ADMIN")
                        // P-06a–e: ADMIN area (BR-01, BR-02, FT-09 NAC-02).
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // P-02 / song browse: curation surfaces, not offered to Customers (spec 2.1).
                        .requestMatchers("/search", "/search/**", Routes.SONGS,
                                Routes.SONGS_PLAY_QUEUE)
                        .hasAnyRole("ADMIN", "CONTENT_DESIGNER")
                        // The play controller checks playlist visibility for Customers.
                        .requestMatchers(Routes.SONG_PLAY).authenticated()
                        // P-03 is a curation surface. A Customer reads shared work
                        // through the Shared Workspace only (FT-06 NAC-03), owns no
                        // playlists (demotion hands them to ADMIN) and never
                        // creates or changes one.
                        .requestMatchers(Routes.PLAYLISTS, Routes.PLAYLISTS + "/**")
                        .hasAnyRole("ADMIN", "CONTENT_DESIGNER")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler))
                .formLogin(form -> form
                        .loginPage(Routes.LOGIN)
                        .loginProcessingUrl(Routes.LOGIN)
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl(Routes.LOGIN + "?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                // FT-01: after the 8 h inactivity window the user returns to P-00
                // with the session-expired notice. Concurrency is unbounded; the
                // registry exists so ADMIN can expire another user's sessions.
                .sessionManagement(session -> session
                        .invalidSessionUrl(Routes.LOGIN + "?expired")
                        .sessionConcurrency(concurrency -> concurrency
                                .maximumSessions(-1)
                                .sessionRegistry(sessionRegistry)
                                .expiredUrl(Routes.LOGIN + "?expired")));

        return http.build();
    }
}
