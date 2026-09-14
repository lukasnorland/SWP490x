package com.funix.swp490x.mrs.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.funix.swp490x.mrs.config.SecurityConfig;
import com.funix.swp490x.mrs.config.WebConfig;
import com.funix.swp490x.mrs.domain.AuditLog;
import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.mail.NotificationService;
import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.CorrelationIdFilter;
import com.funix.swp490x.mrs.security.LoginAttemptService;
import com.funix.swp490x.mrs.security.LoginFailureHandler;
import com.funix.swp490x.mrs.security.LoginSuccessHandler;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.security.MrsUserDetailsService;
import com.funix.swp490x.mrs.security.PasswordResetTokenService;
import com.funix.swp490x.mrs.security.RequestRateLimiter;
import com.funix.swp490x.mrs.service.AuthService;
import com.funix.swp490x.mrs.service.PlaylistService;
import com.funix.swp490x.mrs.web.support.ShellModelAdvice;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Exercises the P-00 sign-in round trip through the real filter chain: the
 * form must carry a CSRF token, correct credentials must reach the right
 * landing page, and a wrong password must come back with the generic banner.
 */
@WebMvcTest(controllers = {AuthController.class, HomeController.class, SearchController.class,
        PlaylistController.class, AccountPasswordController.class})
@Import({SecurityConfig.class, WebConfig.class, ShellModelAdvice.class, LoginSuccessHandler.class,
        LoginFailureHandler.class, LoginAttemptService.class, MrsUserDetailsService.class,
        PasswordResetTokenService.class, AuthService.class, RequestRateLimiter.class})
class LoginFlowTest {

    private static final String PASSWORD = "Admin@2026";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    @MockitoBean
    private NotificationService notificationService;

    /** A Content Designer lands on My Playlists, so P-03a has to render. */
    @MockitoBean
    private PlaylistService playlistService;

    @MockitoBean
    private com.funix.swp490x.mrs.service.SearchService searchService;

    private User account(Role role, boolean mustChangePassword) {
        User user = new User();
        user.setId(1L);
        user.setUsername("Demo " + role.getDisplayName());
        user.setEmail("demo@mrs.local");
        user.setPasswordHash(new BCryptPasswordEncoder().encode(PASSWORD));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(mustChangePassword);
        return user;
    }

    /**
     * Without this hidden field the browser POST is rejected before
     * authentication runs and the user is bounced back to a pristine login
     * page — no error banner, no explanation.
     */
    @Test
    void loginFormCarriesACsrfToken() throws Exception {
        mockMvc.perform(get(Routes.LOGIN))
                .andExpect(content().string(containsString("_csrf")))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().exists(CorrelationIdFilter.HEADER));
    }

    @Test
    void aValidRegisterRequestIsForwardedToTheAdminMailbox() throws Exception {
        mockMvc.perform(registerRequest("new.user@example.com", "203.0.113.1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.LOGIN))
                .andExpect(flash().attribute("flashVariant", "success"))
                .andExpect(flash().attribute("flash", Messages.REGISTER_REQUEST_SENT));

        then(notificationService).should().sendRegistrationRequest("new.user@example.com");
    }

    @Test
    void anInvalidRegisterRequestDoesNotSendMailAndReopensThePanel() throws Exception {
        mockMvc.perform(registerRequest("not-an-email", "203.0.113.2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.LOGIN))
                .andExpect(flash().attribute("registerEmailError", Messages.REGISTER_REQUEST_INVALID_EMAIL))
                .andExpect(flash().attribute("reopenRegisterModal", true));

        then(notificationService).should(never()).sendRegistrationRequest(anyString());
    }

    /** BV-12: three per hour per origin, so the fourth is refused. */
    @Test
    void requestAccount_whenFourthWithinTheHour_shouldReturn429() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(registerRequest("new.user@example.com", "203.0.113.3"))
                    .andExpect(status().is3xxRedirection());
        }

        mockMvc.perform(registerRequest("new.user@example.com", "203.0.113.3"))
                .andExpect(status().isTooManyRequests())
                .andExpect(view().name("auth/login"))
                .andExpect(model().attribute("registerEmailError",
                        Messages.REGISTER_REQUEST_RATE_LIMITED))
                .andExpect(model().attribute("reopenRegisterModal", true));

        // NFR-SEC07: the refused attempt must not reach the mailbox.
        then(notificationService).should(times(3))
                .sendRegistrationRequest("new.user@example.com");
    }

    /**
     * A malformed address is counted too, or a caller could walk past the cap
     * by looping on values that never reach {@code requestAccount}.
     */
    @Test
    void requestAccount_whenAddressesAreMalformed_shouldStillCount() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(registerRequest("not-an-email", "203.0.113.4"))
                    .andExpect(status().is3xxRedirection());
        }

        mockMvc.perform(registerRequest("still.valid@example.com", "203.0.113.4"))
                .andExpect(status().isTooManyRequests());
    }

    /**
     * Behind Nginx every request shares one {@code getRemoteAddr}, so the cap
     * has to read the forwarded hop or one caller would lock out everybody.
     */
    @Test
    void requestAccount_shouldCountAgainstTheForwardedHopNotTheProxy() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(registerRequest("new.user@example.com", "203.0.113.5, 10.0.0.1"))
                    .andExpect(status().is3xxRedirection());
        }

        mockMvc.perform(registerRequest("new.user@example.com", "203.0.113.6, 10.0.0.1"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(registerRequest("new.user@example.com", "203.0.113.5, 10.0.0.1"))
                .andExpect(status().isTooManyRequests());
    }

    /**
     * The limiter is a singleton for the whole slice, so each test names its
     * own origin and stays independent of the order they run in.
     */
    private static MockHttpServletRequestBuilder registerRequest(String email, String origin) {
        return post(Routes.REGISTER_REQUEST)
                .header("X-Forwarded-For", origin)
                .param("email", email)
                .with(csrf());
    }

    @Test
    void correctCredentialsReachTheRoleLandingPage() throws Exception {
        given(userRepository.findByEmail(anyString()))
                .willReturn(Optional.of(account(Role.CONTENT_DESIGNER, false)));

        mockMvc.perform(post(Routes.LOGIN)
                        .param("email", "demo@mrs.local")
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.SEARCH));
    }

    @Test
    void anAccountOwingAPasswordChangeGoesThereFirst() throws Exception {
        given(userRepository.findByEmail(anyString()))
                .willReturn(Optional.of(account(Role.CONTENT_DESIGNER, true)));

        mockMvc.perform(post(Routes.LOGIN)
                        .param("email", "demo@mrs.local")
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.PASSWORD_CHANGE));
    }

    /**
     * Flow F-04 runs forced change straight through to the role landing page.
     * Sending the user back to P-00 instead reads as a failed login.
     */
    @Test
    void completingTheForcedChangeLandsOnTheRolePageRatherThanBackAtLogin() throws Exception {
        User pending = account(Role.CONTENT_DESIGNER, true);
        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(pending));

        mockMvc.perform(post(Routes.PASSWORD_CHANGE)
                        .param("currentPassword", PASSWORD)
                        .param("password", "FreshPass@2026")
                        .param("confirmPassword", "FreshPass@2026")
                        .with(user(new MrsUserDetails(pending, true)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.SEARCH));
    }

    @Test
    void aForcedChangeThatFailsThePolicyStaysOnTheScreen() throws Exception {
        User pending = account(Role.CONTENT_DESIGNER, true);
        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(pending));

        mockMvc.perform(post(Routes.PASSWORD_CHANGE)
                        .param("currentPassword", PASSWORD)
                        .param("password", "weak")
                        .param("confirmPassword", "weak")
                        .with(user(new MrsUserDetails(pending, true)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Include an uppercase letter")));
    }

    /**
     * The interceptor holds a pending account on the change-password screen by
     * redirecting everything else, so assets have to be exempt. When
     * {@code /vendor/**} was missing from the exemptions, Bootstrap's stylesheet
     * answered with a redirect to that screen and it rendered unstyled.
     */
    @ParameterizedTest
    @ValueSource(strings = {"/vendor/bootstrap/bootstrap.min.css", "/css/tokens.css", "/js/mrs.js"})
    void assetsStayReachableWhileAPasswordChangeIsPending(String asset) throws Exception {
        User pending = account(Role.CONTENT_DESIGNER, true);

        mockMvc.perform(get(asset).with(user(new MrsUserDetails(pending, true))))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordComesBackWithTheGenericBanner() throws Exception {
        given(userRepository.findByEmail(anyString()))
                .willReturn(Optional.of(account(Role.CONTENT_DESIGNER, false)));

        mockMvc.perform(post(Routes.LOGIN)
                        .param("email", "demo@mrs.local")
                        .param("password", "WrongPassword@1")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.LOGIN + "?error"));

        then(auditLogRepository).should().save(org.mockito.ArgumentMatchers.argThat(log ->
                AuditLog.ACTION_LOGIN_FAILED.equals(log.getAction())
                        && log.getActorId().equals(1L)));
    }

    @Test
    void anUnknownEmailIsIndistinguishableFromAWrongPassword() throws Exception {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

        mockMvc.perform(post(Routes.LOGIN)
                        .param("email", "nobody@mrs.local")
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.LOGIN + "?error"));

        then(auditLogRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }
}
