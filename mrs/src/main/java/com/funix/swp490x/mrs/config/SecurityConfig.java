package com.funix.swp490x.mrs.config;

import com.funix.swp490x.mrs.security.EagerCsrfTokenFilter;
import com.funix.swp490x.mrs.security.LoginFailureHandler;
import com.funix.swp490x.mrs.security.LoginSuccessHandler;
import com.funix.swp490x.mrs.web.Routes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
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

    /** BCrypt only (NFR-SEC02); matches the {@code $2a$} hashes seeded by V2. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            LoginSuccessHandler successHandler, LoginFailureHandler failureHandler,
            SessionRegistry sessionRegistry) throws Exception {

        http
                // CsrfFilter publishes the deferred token; this reads it while the
                // response is still uncommitted, so no template can trigger session
                // creation mid-render.
                .addFilterAfter(new EagerCsrfTokenFilter(), CsrfFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(Routes.STATIC_ASSETS)
                        .permitAll()
                        .requestMatchers(Routes.LOGIN, Routes.REGISTER_REQUEST,
                                Routes.PASSWORD_RESET, Routes.PASSWORD_RESET + "/**")
                        .permitAll()
                        // P-06a–e: ADMIN area (BR-01, BR-02, FT-09 NAC-02).
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // P-02 / song browse: curation surfaces, not offered to Customers (spec 2.1).
                        .requestMatchers(Routes.SEARCH, Routes.SONGS)
                        .hasAnyRole("ADMIN", "CONTENT_DESIGNER")
                        .anyRequest().authenticated())
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
