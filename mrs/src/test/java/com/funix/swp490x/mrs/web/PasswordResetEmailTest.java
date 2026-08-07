package com.funix.swp490x.mrs.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funix.swp490x.mrs.config.SecurityConfig;
import com.funix.swp490x.mrs.config.WebConfig;
import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.mail.MailConfig;
import com.funix.swp490x.mrs.mail.MailDeliveryException;
import com.funix.swp490x.mrs.mail.MailTransport;
import com.funix.swp490x.mrs.mail.NotificationService;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.LoginAttemptService;
import com.funix.swp490x.mrs.security.LoginFailureHandler;
import com.funix.swp490x.mrs.security.LoginSuccessHandler;
import com.funix.swp490x.mrs.security.MrsUserDetailsService;
import com.funix.swp490x.mrs.security.PasswordResetTokenService;
import com.funix.swp490x.mrs.web.support.ShellModelAdvice;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * P-01 / UC-02 delivery. The link used to be written to the log, where only a
 * developer watching the console could find it.
 *
 * <p>{@link MailTransport} is the only thing mocked, so the body asserted on
 * here is produced by the real template through the real engine.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, WebConfig.class, ShellModelAdvice.class, LoginSuccessHandler.class,
        LoginFailureHandler.class, LoginAttemptService.class, MrsUserDetailsService.class,
        PasswordResetTokenService.class, MailConfig.class, NotificationService.class})
@TestPropertySource(properties = "mrs.mail.from=no-reply@mrs.local")
class PasswordResetEmailTest {

    private static final String EMAIL = "designer@mrs.local";
    private static final String CONFIRMATION = "a reset link is on its way";
    private static final String ADMIN_MAILBOX = "no-reply@mrs.local";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MailTransport mailTransport;

    private static User registeredAccount() {
        User user = new User();
        user.setId(1L);
        user.setUsername("Demo Designer");
        user.setEmail(EMAIL);
        user.setPasswordHash("{noop}irrelevant");
        user.setRole(Role.CONTENT_DESIGNER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    @Test
    void aRegisteredAddressIsSentAUsableLink() throws Exception {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(registeredAccount()));

        mockMvc.perform(post(Routes.PASSWORD_RESET).param("email", EMAIL).with(csrf()))
                .andExpect(status().isOk());

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        then(mailTransport).should().send(eq(EMAIL), anyString(), body.capture());

        assertThat(body.getValue())
                .contains("http://localhost:8080" + Routes.PASSWORD_RESET_SET + "?token=")
                .contains("30 minutes");
    }

    /** P-01 must not disclose whether an address is registered (FT-01). */
    @Test
    void anUnknownAddressIsSentNothingAndLooksIdentical() throws Exception {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

        mockMvc.perform(post(Routes.PASSWORD_RESET).param("email", "nobody@mrs.local").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(CONFIRMATION)));

        then(mailTransport).should(never()).send(anyString(), anyString(), anyString());
    }

    /**
     * Reporting a delivery failure would answer the question the screen refuses
     * to answer, so the confirmation has to stand regardless.
     */
    @Test
    void aFailedDeliveryStillShowsTheSameConfirmation() throws Exception {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(registeredAccount()));
        willThrow(new MailDeliveryException("smtp is down", new IllegalStateException()))
                .given(mailTransport).send(anyString(), anyString(), anyString());

        mockMvc.perform(post(Routes.PASSWORD_RESET).param("email", EMAIL).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(CONFIRMATION)));
    }

    @Test
    void aRegisterRequestIsMailedToTheConfiguredAdminMailbox() throws Exception {
        String candidateEmail = "candidate@example.com";

        mockMvc.perform(post(Routes.REGISTER_REQUEST).param("email", candidateEmail).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.LOGIN));

        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        then(mailTransport).should().send(to.capture(), subject.capture(), body.capture());

        assertThat(to.getValue()).isEqualTo(ADMIN_MAILBOX);
        assertThat(subject.getValue()).isEqualTo("New MRS registration request");
        assertThat(body.getValue())
                .contains(candidateEmail)
                .contains("http://localhost:8080" + Routes.LOGIN);
    }
}
