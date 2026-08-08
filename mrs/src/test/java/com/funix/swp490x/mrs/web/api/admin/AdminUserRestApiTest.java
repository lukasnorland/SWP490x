package com.funix.swp490x.mrs.web.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import com.funix.swp490x.mrs.mail.SesIdentityException;
import com.funix.swp490x.mrs.mail.SesIdentityService;
import com.funix.swp490x.mrs.mail.SesIdentityService.Outcome;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.LoginAttemptService;
import com.funix.swp490x.mrs.security.LoginFailureHandler;
import com.funix.swp490x.mrs.security.LoginSuccessHandler;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.security.MrsUserDetailsService;
import com.funix.swp490x.mrs.security.SessionInvalidationService;
import com.funix.swp490x.mrs.service.UserAccountService;
import com.funix.swp490x.mrs.web.Messages;
import com.funix.swp490x.mrs.web.Routes;
import com.funix.swp490x.mrs.web.api.ApiExceptionHandler;
import com.funix.swp490x.mrs.web.support.ShellModelAdvice;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spring REST CRUD for P-06a ({@link AdminUserRestController}).
 */
@WebMvcTest(controllers = AdminUserRestController.class)
@Import({SecurityConfig.class, WebConfig.class, ShellModelAdvice.class, LoginSuccessHandler.class,
        LoginFailureHandler.class, LoginAttemptService.class, MrsUserDetailsService.class,
        MailConfig.class, NotificationService.class, UserAccountService.class,
        ApiExceptionHandler.class})
class AdminUserRestApiTest {

    private static final String STRONG_PASSWORD = "Fresh-Start@2026";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MailTransport mailTransport;

    @MockitoBean
    private SesIdentityService sesIdentityService;

    @MockitoBean
    private JavaMailSender javaMailSender;

    @MockitoBean
    private SessionInvalidationService sessionInvalidationService;

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
    void stubs() {
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(42L);
            }
            return saved;
        });
        given(sesIdentityService.isVerified(anyString())).willReturn(true);
        given(userRepository.search(nullable(Role.class), nullable(UserStatus.class),
                nullable(String.class), any(Pageable.class)))
                .willReturn(Page.empty());
    }

    @Test
    void listReturnsPagedJson() throws Exception {
        User existing = activeDesigner(42L);
        given(userRepository.search(nullable(Role.class), nullable(UserStatus.class),
                nullable(String.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(existing)));

        mockMvc.perform(get(Routes.API_ADMIN_USERS)
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("nina@mrs.local"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void creatingAnAccountEmailsItsCredentials() throws Exception {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

        mockMvc.perform(post(Routes.API_ADMIN_USERS)
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nina Designer","email":"nina@mrs.local",
                                 "role":"CONTENT_DESIGNER","password":"%s"}
                                """.formatted(STRONG_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailDelivered").value(true))
                .andExpect(jsonPath("$.message").value(Messages.USER_CREATED))
                .andExpect(jsonPath("$.user.email").value("nina@mrs.local"));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        then(mailTransport).should().send(eq("nina@mrs.local"), anyString(), body.capture());
        assertThat(body.getValue()).contains(STRONG_PASSWORD).contains("nina@mrs.local");
    }

    @Test
    void aDuplicateEmailIsRejectedWithoutCreatingAnything() throws Exception {
        User existing = new User();
        existing.setEmail("nina@mrs.local");
        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(existing));

        mockMvc.perform(post(Routes.API_ADMIN_USERS)
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nina Designer","email":"nina@mrs.local",
                                 "role":"CONTENT_DESIGNER","password":"%s"}
                                """.formatted(STRONG_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(Messages.DUPLICATE_EMAIL));

        then(userRepository).should(never()).save(any(User.class));
    }

    @Test
    void aWeakInitialPasswordIsRejectedWithTheUnmetRule() throws Exception {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

        mockMvc.perform(post(Routes.API_ADMIN_USERS)
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nina Designer","email":"nina@mrs.local",
                                 "role":"CONTENT_DESIGNER","password":"weak"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("Include an uppercase letter")));

        then(userRepository).should(never()).save(any(User.class));
    }

    @Test
    void anUndeliverableMessageLeavesTheAccountInPlace() throws Exception {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());
        willThrow(new MailDeliveryException("smtp is down", new IllegalStateException()))
                .given(mailTransport).send(anyString(), anyString(), anyString());

        mockMvc.perform(post(Routes.API_ADMIN_USERS)
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nina Designer","email":"nina@mrs.local",
                                 "role":"CONTENT_DESIGNER","password":"%s"}
                                """.formatted(STRONG_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailDelivered").value(false))
                .andExpect(jsonPath("$.message").value(Messages.USER_CREATED_EMAIL_FAILED));

        then(userRepository).should().save(any(User.class));
    }

    @Test
    void deleteSoftDeactivatesAndExpiresSessions() throws Exception {
        User existing = activeDesigner(42L);
        given(userRepository.findById(42L)).willReturn(Optional.of(existing));

        mockMvc.perform(delete(Routes.API_ADMIN_USERS + "/42")
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEACTIVATED"));

        assertThat(existing.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        then(sessionInvalidationService).should().invalidateSessionsForEmail("nina@mrs.local");
    }

    @Test
    void patchUpdatesRoleAndStatus() throws Exception {
        User existing = activeDesigner(42L);
        given(userRepository.findById(42L)).willReturn(Optional.of(existing));

        mockMvc.perform(patch(Routes.API_ADMIN_USERS + "/42")
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"CUSTOMER","status":"DEACTIVATED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("DEACTIVATED"));
    }

    @Test
    void selfDeleteIsForbidden() throws Exception {
        User self = new User();
        self.setId(1L);
        self.setEmail("admin@mrs.local");
        self.setRole(Role.ADMIN);
        self.setStatus(UserStatus.ACTIVE);
        given(userRepository.findById(1L)).willReturn(Optional.of(self));

        mockMvc.perform(delete(Routes.API_ADMIN_USERS + "/1")
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(Messages.SELF_MODIFICATION_FORBIDDEN));
    }

    @Test
    void resendingIssuesAFreshPasswordAndSendsItAgain() throws Exception {
        User existing = activeDesigner(42L);
        given(userRepository.findById(42L)).willReturn(Optional.of(existing));

        mockMvc.perform(post(Routes.API_ADMIN_USERS + "/42/credentials/resend")
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailDelivered").value(true))
                .andExpect(jsonPath("$.message").value(Messages.CREDENTIALS_RESENT));

        then(mailTransport).should().send(eq("nina@mrs.local"), anyString(), anyString());
        assertThat(existing.isMustChangePassword()).isTrue();
    }

    @Test
    void creationIsClosedToEveryoneButAdmin() throws Exception {
        User designer = activeDesigner(2L);

        mockMvc.perform(post(Routes.API_ADMIN_USERS)
                        .with(user(new MrsUserDetails(designer, true)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Someone Else","email":"someone@mrs.local",
                                 "role":"CUSTOMER","password":"%s"}
                                """.formatted(STRONG_PASSWORD)))
                .andExpect(status().isForbidden());

        then(userRepository).should(never()).save(any(User.class));
    }

    @Test
    void preparingARecipientAsksSesToVerifyTheAddress() throws Exception {
        given(sesIdentityService.prepareRecipient("nina@example.com"))
                .willReturn(Outcome.VERIFICATION_SENT);

        mockMvc.perform(post(Routes.API_ADMIN_USERS + "/ses-recipients")
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nina@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sent"))
                .andExpect(jsonPath("$.message").value(Messages.SES_VERIFICATION_SENT));
    }

    @Test
    void createIsBlockedUntilTheRecipientIsVerifiedWithSes() throws Exception {
        given(sesIdentityService.isVerified("nina@example.com")).willReturn(false);

        mockMvc.perform(post(Routes.API_ADMIN_USERS)
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nina Designer","email":"nina@example.com",
                                 "role":"CONTENT_DESIGNER","password":"%s"}
                                """.formatted(STRONG_PASSWORD)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(Messages.SES_RECIPIENT_NOT_VERIFIED));

        then(userRepository).should(never()).save(any(User.class));
    }

    @Test
    void preparingAnAlreadyVerifiedRecipientIsReportedAsReady() throws Exception {
        given(sesIdentityService.prepareRecipient("nina@example.com"))
                .willReturn(Outcome.ALREADY_VERIFIED);

        mockMvc.perform(post(Routes.API_ADMIN_USERS + "/ses-recipients")
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nina@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("already_verified"));
    }

    @Test
    void sesFailureIsReportedWithoutCreatingAnAccount() throws Exception {
        given(sesIdentityService.prepareRecipient("nina@example.com"))
                .willThrow(new SesIdentityException("no credentials"));

        mockMvc.perform(post(Routes.API_ADMIN_USERS + "/ses-recipients")
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nina@example.com\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value("error"));
    }

    private static User activeDesigner(Long id) {
        User existing = new User();
        existing.setId(id);
        existing.setUsername("Nina Designer");
        existing.setEmail("nina@mrs.local");
        existing.setPasswordHash("{noop}the-old-one");
        existing.setRole(Role.CONTENT_DESIGNER);
        existing.setStatus(UserStatus.ACTIVE);
        existing.setMustChangePassword(false);
        return existing;
    }
}
