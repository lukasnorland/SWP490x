package com.funix.swp490x.mrs.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.security.MrsUserDetailsService;
import com.funix.swp490x.mrs.service.UserAccountService;
import com.funix.swp490x.mrs.web.Messages;
import com.funix.swp490x.mrs.web.Routes;
import com.funix.swp490x.mrs.web.support.ShellModelAdvice;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * P-06a account creation and the credentials message it must send (UC-07,
 * BR-15).
 *
 * <p>Only the repository and the mail transport are mocked, so the rules of
 * UC-07 and the rendered message are both exercised for real.
 */
@WebMvcTest(controllers = AdminUserController.class)
@Import({SecurityConfig.class, WebConfig.class, ShellModelAdvice.class, LoginSuccessHandler.class,
        LoginFailureHandler.class, LoginAttemptService.class, MrsUserDetailsService.class,
        MailConfig.class, NotificationService.class, UserAccountService.class})
class AdminUserManagementTest {

    private static final String STRONG_PASSWORD = "Fresh-Start@2026";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MailTransport mailTransport;

    private static MrsUserDetails admin() {
        User user = new User();
        user.setId(1L);
        user.setUsername("System Admin");
        user.setEmail("admin@mrs.local");
        user.setPasswordHash("{noop}irrelevant");
        user.setRole(Role.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(false);
        return new MrsUserDetails(user, true);
    }

    @BeforeEach
    void savedAccountsComeBackWithAnId() {
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });
    }

    private MockHttpServletRequestBuilder createRequest(String email, String password) {
        return post(Routes.ADMIN_USERS)
                .param("name", "Nina Designer")
                .param("email", email)
                .param("role", Role.CONTENT_DESIGNER.name())
                .param("password", password)
                .with(user(admin()))
                .with(csrf());
    }

    @Test
    void creatingAnAccountEmailsItsCredentials() throws Exception {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

        mockMvc.perform(createRequest("nina@mrs.local", STRONG_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.ADMIN_USERS))
                .andExpect(flash().attribute("flash", Messages.USER_CREATED));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        then(mailTransport).should().send(eq("nina@mrs.local"), anyString(), body.capture());

        assertThat(body.getValue())
                .contains(STRONG_PASSWORD)
                .contains("nina@mrs.local")
                .contains("Content Designer");
    }

    /** FT-09 AC-01: the account arrives owing a password change. */
    @Test
    void aNewAccountIsActiveAndOwesAPasswordChange() throws Exception {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

        mockMvc.perform(createRequest("nina@mrs.local", STRONG_PASSWORD));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(saved.capture());

        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getValue().isMustChangePassword()).isTrue();
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo(STRONG_PASSWORD);
    }

    /** E1 / NAC-01 / DC-06. */
    @Test
    void aDuplicateEmailIsRejectedWithoutCreatingAnything() throws Exception {
        User existing = new User();
        existing.setEmail("nina@mrs.local");
        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(existing));

        mockMvc.perform(createRequest("nina@mrs.local", STRONG_PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString(Messages.DUPLICATE_EMAIL)));

        then(userRepository).should(never()).save(any(User.class));
        then(mailTransport).should(never()).send(anyString(), anyString(), anyString());
    }

    /**
     * BR-15 posts the only password the account will ever be given to this
     * address, so an unreachable one produces an account nobody can sign in to.
     * The browser's email field is not the boundary; this is.
     */
    @Test
    void anAddressThatCannotReachAMailboxIsRejected() throws Exception {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

        mockMvc.perform(createRequest("nina-at-mrs-local", STRONG_PASSWORD))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString(Messages.INVALID_EMAIL)));

        then(userRepository).should(never()).save(any(User.class));
        then(mailTransport).should(never()).send(anyString(), anyString(), anyString());
    }

    /** E2 / NAC-04: no account at all rather than one with a weak password. */
    @Test
    void aWeakInitialPasswordIsRejectedWithTheUnmetRule() throws Exception {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

        mockMvc.perform(createRequest("nina@mrs.local", "weak"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("Include an uppercase letter")));

        then(userRepository).should(never()).save(any(User.class));
    }

    /**
     * E3: the account stands when the Email Service does not, and ADMIN is told
     * so they can resend rather than assuming the user has their password.
     */
    @Test
    void anUndeliverableMessageLeavesTheAccountInPlace() throws Exception {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());
        willThrow(new MailDeliveryException("smtp is down", new IllegalStateException()))
                .given(mailTransport).send(anyString(), anyString(), anyString());

        mockMvc.perform(createRequest("nina@mrs.local", STRONG_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flash", Messages.USER_CREATED_EMAIL_FAILED));

        then(userRepository).should().save(any(User.class));
    }

    @Test
    void resendingIssuesAFreshPasswordAndSendsItAgain() throws Exception {
        User existing = new User();
        existing.setId(42L);
        existing.setUsername("Nina Designer");
        existing.setEmail("nina@mrs.local");
        existing.setPasswordHash("{noop}the-old-one");
        existing.setRole(Role.CONTENT_DESIGNER);
        existing.setStatus(UserStatus.ACTIVE);
        existing.setMustChangePassword(false);
        given(userRepository.findById(42L)).willReturn(Optional.of(existing));

        mockMvc.perform(post("/admin/users/42/resend-credentials")
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flash", Messages.CREDENTIALS_RESENT));

        then(mailTransport).should().send(eq("nina@mrs.local"), anyString(), anyString());
        assertThat(existing.getPasswordHash()).isNotEqualTo("{noop}the-old-one");
        assertThat(existing.isMustChangePassword()).isTrue();
    }

    /** BR-01: the area is ADMIN-only, and the POST is no exception. */
    @Test
    void creationIsClosedToEveryoneButAdmin() throws Exception {
        User designer = new User();
        designer.setId(2L);
        designer.setUsername("Nina Designer");
        designer.setEmail("nina@mrs.local");
        designer.setPasswordHash("{noop}irrelevant");
        designer.setRole(Role.CONTENT_DESIGNER);
        designer.setStatus(UserStatus.ACTIVE);
        designer.setMustChangePassword(false);

        mockMvc.perform(post(Routes.ADMIN_USERS)
                        .param("name", "Someone Else")
                        .param("email", "someone@mrs.local")
                        .param("role", Role.CUSTOMER.name())
                        .param("password", STRONG_PASSWORD)
                        .with(user(new MrsUserDetails(designer, true)))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        then(userRepository).should(never()).save(any(User.class));
    }
}
