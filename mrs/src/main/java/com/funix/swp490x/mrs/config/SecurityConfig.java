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

/** Enforces authentication and role access server-side (NFR-SEC03). */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Allows inline styles required by Bootstrap; scripts and fonts remain self-hosted. */
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

    /** Disables unused browser capabilities (TDS 5.5). */
    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=()";

    /** BCrypt only (NFR-SEC02); cost 12. Seeded V2 hashes are {@code $2a$10$}
     *  and still verify via {@code matches}. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Registers active sessions for account deactivation and role-change revocation. */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Publishes servlet session lifecycle events to {@link SessionRegistry}. */
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
                // Resolve CSRF before rendering can commit the response.
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
                        // Customers use only the Shared Workspace and never own or edit playlists (BR-04).
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
                // Expired sessions return to login; the registry also supports ADMIN-triggered revocation.
                .sessionManagement(session -> session
                        .invalidSessionUrl(Routes.LOGIN + "?expired")
                        .sessionConcurrency(concurrency -> concurrency
                                .maximumSessions(-1)
                                .sessionRegistry(sessionRegistry)
                                .expiredUrl(Routes.LOGIN + "?expired")));

        return http.build();
    }
}
