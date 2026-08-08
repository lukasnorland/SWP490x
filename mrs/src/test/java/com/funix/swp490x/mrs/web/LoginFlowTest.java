package com.funix.swp490x.mrs.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funix.swp490x.mrs.config.SecurityConfig;
import com.funix.swp490x.mrs.config.WebConfig;
import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.mail.NotificationService;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.LoginAttemptService;
import com.funix.swp490x.mrs.security.LoginFailureHandler;
import com.funix.swp490x.mrs.security.LoginSuccessHandler;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.security.MrsUserDetailsService;
import com.funix.swp490x.mrs.security.PasswordResetTokenService;
import com.funix.swp490x.mrs.web.api.AccountPasswordRestController;
import com.funix.swp490x.mrs.web.api.ApiExceptionHandler;
import com.funix.swp490x.mrs.web.api.AuthRestController;
import com.funix.swp490x.mrs.web.support.ShellModelAdvice;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the P-00 sign-in round trip through the real filter chain: the
 * form must carry a CSRF token, correct credentials must reach the right
 * landing page, and a wrong password must come back with the generic banner.
 */
@WebMvcTest(controllers = {AuthController.class, AuthRestController.class, HomeController.class,
        SearchController.class, PlaylistController.class, AccountPasswordController.class,
        AccountPasswordRestController.class})
@Import({SecurityConfig.class, WebConfig.class, ShellModelAdvice.class, LoginSuccessHandler.class,
        LoginFailureHandler.class, LoginAttemptService.class, MrsUserDetailsService.class,
        PasswordResetTokenService.class, ApiExceptionHandler.class})
class LoginFlowTest {

    private static final String PASSWORD = "Admin@2026";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private NotificationService notificationService;

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

    @Test
    void loginFormCarriesACsrfToken() throws Exception {
        mockMvc.perform(get(Routes.LOGIN))
                .andExpect(content().string(containsString("_csrf")));
    }

    @Test
    void aValidRegisterRequestIsForwardedToTheAdminMailbox() throws Exception {
        mockMvc.perform(post(Routes.API_REGISTER_REQUEST)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new.user@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(Messages.REGISTER_REQUEST_SENT));

        then(notificationService).should().sendRegistrationRequest("new.user@example.com");
    }

    @Test
    void anInvalidRegisterRequestDoesNotSendMail() throws Exception {
        mockMvc.perform(post(Routes.API_REGISTER_REQUEST)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(Messages.REGISTER_REQUEST_INVALID_EMAIL));

        then(notificationService).should(never()).sendRegistrationRequest(anyString());
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

    @Test
    void completingTheForcedChangeLandsOnTheRolePageRatherThanBackAtLogin() throws Exception {
        User pending = account(Role.CONTENT_DESIGNER, true);
        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(pending));

        mockMvc.perform(put(Routes.API_ACCOUNT_PASSWORD)
                        .with(user(new MrsUserDetails(pending, true)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","password":"FreshPass@2026",
                                 "confirmPassword":"FreshPass@2026"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectTo").value(Routes.SEARCH));
    }

    @Test
    void aForcedChangeThatFailsThePolicyStaysOnTheScreen() throws Exception {
        User pending = account(Role.CONTENT_DESIGNER, true);
        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(pending));

        mockMvc.perform(put(Routes.API_ACCOUNT_PASSWORD)
                        .with(user(new MrsUserDetails(pending, true)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","password":"weak","confirmPassword":"weak"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("Include an uppercase letter")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/css/tokens.css", "/vendor/bootstrap/bootstrap.min.css", "/js/mrs.js"})
    void staticAssetsEscapeTheForcedPasswordGate(String path) throws Exception {
        User pending = account(Role.CONTENT_DESIGNER, true);

        mockMvc.perform(get(path).with(user(new MrsUserDetails(pending, true))))
                .andExpect(status().isOk());
    }

    @Test
    void aWrongPasswordShowsTheGenericBanner() throws Exception {
        given(userRepository.findByEmail(anyString()))
                .willReturn(Optional.of(account(Role.CONTENT_DESIGNER, false)));

        mockMvc.perform(post(Routes.LOGIN)
                        .param("email", "demo@mrs.local")
                        .param("password", "wrong-password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.LOGIN + "?error"));
    }

    @Test
    void anUnknownEmailShowsTheSameGenericBanner() throws Exception {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

        mockMvc.perform(post(Routes.LOGIN)
                        .param("email", "ghost@mrs.local")
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.LOGIN + "?error"));
    }
}
