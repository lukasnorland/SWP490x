package com.funix.swp490x.mrs.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funix.swp490x.mrs.config.SecurityConfig;
import com.funix.swp490x.mrs.config.WebConfig;
import com.funix.swp490x.mrs.domain.PlaylistStatus;
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
import com.funix.swp490x.mrs.service.PlaylistOwner;
import com.funix.swp490x.mrs.service.PlaylistService;
import com.funix.swp490x.mrs.service.PlaylistSuccessorChoice;
import com.funix.swp490x.mrs.service.PlaylistSuccessorRequiredException;
import com.funix.swp490x.mrs.service.UserAccountService;
import com.funix.swp490x.mrs.web.Messages;
import com.funix.swp490x.mrs.web.Routes;
import com.funix.swp490x.mrs.web.support.ShellModelAdvice;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
import org.springframework.mail.javamail.JavaMailSender;
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

    @MockitoBean
    private SesIdentityService sesIdentityService;

    @MockitoBean
    private JavaMailSender javaMailSender;

    @MockitoBean
    private SessionInvalidationService sessionInvalidationService;

    /** Deactivation and demotion reassign owned playlists through this. */
    @MockitoBean
    private PlaylistService playlistService;

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
        // Real SMTP is present in this slice, so create() requires a verified
        // SES recipient unless a test overrides this stub.
        given(sesIdentityService.isVerified(anyString())).willReturn(true);
        // Rejected creates re-render the list; keep the page empty.
        given(userRepository.search(nullable(Role.class), nullable(UserStatus.class),
                nullable(String.class), any(Pageable.class)))
                .willReturn(Page.empty());
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
        User existing = activeDesigner(42L);
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

    @Test
    void anAdminCannotResendCredentialsForThemselves() throws Exception {
        User self = new User();
        self.setId(1L);
        self.setEmail("admin@mrs.local");
        self.setRole(Role.ADMIN);
        self.setStatus(UserStatus.ACTIVE);
        self.setPasswordHash("{noop}the-old-one");
        given(userRepository.findById(1L)).willReturn(Optional.of(self));

        mockMvc.perform(post("/admin/users/1/resend-credentials")
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flash", Messages.SELF_MODIFICATION_FORBIDDEN));

        then(mailTransport).should(never()).send(anyString(), anyString(), anyString());
        assertThat(self.getPasswordHash()).isEqualTo("{noop}the-old-one");
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

    @Test
    void theRoleFilterOmitsAdmin() throws Exception {
        // ADMIN is neither filterable nor listed on P-06a (managed roles only).
        mockMvc.perform(get(Routes.ADMIN_USERS).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filterRoles", UserAccountService.ASSIGNABLE_ROLES));
    }

    @Test
    void theAccountListRendersViewRecordsWithoutThePasswordHash() throws Exception {
        User existing = activeDesigner(42L);
        existing.setCreatedAt(LocalDateTime.of(2026, 8, 1, 12, 0));
        given(userRepository.search(nullable(Role.class), nullable(UserStatus.class),
                nullable(String.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(existing)));

        mockMvc.perform(get(Routes.ADMIN_USERS).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nina Designer")))
                .andExpect(content().string(containsString("nina@mrs.local")))
                .andExpect(content().string(not(containsString("{noop}the-old-one"))));
    }

    @Test
    void preparingARecipientAsksSesToVerifyTheAddress() throws Exception {
        given(sesIdentityService.prepareRecipient("nina@example.com"))
                .willReturn(Outcome.VERIFICATION_SENT);

        mockMvc.perform(post(Routes.ADMIN_USER_PREPARE_RECIPIENT)
                        .param("email", "nina@example.com")
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sent"))
                .andExpect(jsonPath("$.message").value(Messages.SES_VERIFICATION_SENT));
    }

    @Test
    void preparingAnAlreadyVerifiedRecipientIsReportedAsReady() throws Exception {
        given(sesIdentityService.prepareRecipient("nina@example.com"))
                .willReturn(Outcome.ALREADY_VERIFIED);

        mockMvc.perform(post(Routes.ADMIN_USER_PREPARE_RECIPIENT)
                        .param("email", "nina@example.com")
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("already_verified"))
                .andExpect(jsonPath("$.message").value(Messages.SES_ALREADY_VERIFIED));
    }

    @Test
    void preparingAMalformedRecipientIsRejectedWithoutCallingSes() throws Exception {
        mockMvc.perform(post(Routes.ADMIN_USER_PREPARE_RECIPIENT)
                        .param("email", "not-an-email")
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(Messages.INVALID_EMAIL));

        then(sesIdentityService).should(never()).prepareRecipient(anyString());
    }

    @Test
    void aSesFailureIsReportedWithoutCreatingAnAccount() throws Exception {
        given(sesIdentityService.prepareRecipient("nina@example.com"))
                .willThrow(new SesIdentityException("no credentials"));

        mockMvc.perform(post(Routes.ADMIN_USER_PREPARE_RECIPIENT)
                        .param("email", "nina@example.com")
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(Messages.SES_VERIFICATION_FAILED));

        then(userRepository).should(never()).save(any(User.class));
    }

    @Test
    void createIsBlockedUntilTheRecipientIsVerifiedWithSes() throws Exception {
        given(sesIdentityService.isVerified("nina@example.com")).willReturn(false);

        mockMvc.perform(createRequest("nina@example.com", STRONG_PASSWORD))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString(Messages.SES_RECIPIENT_NOT_VERIFIED)));

        then(userRepository).should(never()).save(any(User.class));
        then(mailTransport).should(never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void deactivatingAnAccountExpiresItsSessionsAndTellsTheHolder() throws Exception {
        User existing = activeDesigner(42L);
        given(userRepository.findById(42L)).willReturn(Optional.of(existing));

        mockMvc.perform(post("/admin/users/42/deactivate")
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flash", Messages.USER_DEACTIVATED));

        assertThat(existing.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        then(sessionInvalidationService).should().invalidateSessionsForEmail("nina@mrs.local");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        then(mailTransport).should().send(eq("nina@mrs.local"),
                eq("Your MRS account has been deactivated"), body.capture());
        assertThat(body.getValue()).contains("Nina Designer").contains("deactivated");
    }

    @Test
    void deactivatingADesignerAsksForASuccessorPerPlaylist() throws Exception {
        User existing = activeDesigner(42L);
        given(userRepository.findById(42L)).willReturn(Optional.of(existing));
        givenOwnedPlaylistsNeedSuccessors();

        mockMvc.perform(post("/admin/users/42/deactivate")
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users/42/reassign?intent=deactivate"));

        assertThat(existing.getStatus()).isEqualTo(UserStatus.ACTIVE);
        then(mailTransport).should(never()).send(anyString(), anyString(), anyString());

        mockMvc.perform(get("/admin/users/42/reassign")
                        .param("intent", "deactivate")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Morning coffee")))
                .andExpect(content().string(containsString("Dana Designer")))
                .andExpect(content().string(containsString("Evening mix")))
                .andExpect(content().string(containsString("System Admin (ADMIN)")));
    }

    @Test
    void deactivatingADesignerTransfersEachPlaylistToTheChosenSuccessor() throws Exception {
        User existing = activeDesigner(42L);
        given(userRepository.findById(42L)).willReturn(Optional.of(existing));
        given(playlistService.transferOwnedPlaylists(eq(42L), eq(Map.of(11L, 15L, 12L, 1L)), eq(1L)))
                .willReturn(2);

        mockMvc.perform(post("/admin/users/42/reassign")
                        .param("intent", "deactivate")
                        .param("successor[11]", "15")
                        .param("successor[12]", "1")
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flash",
                        Messages.USER_DEACTIVATED + " " + Messages.playlistsTransferred(2)));

        assertThat(existing.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        then(playlistService).should().transferOwnedPlaylists(eq(42L),
                eq(Map.of(11L, 15L, 12L, 1L)), eq(1L));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        then(mailTransport).should().send(eq("nina@mrs.local"), anyString(), body.capture());
        assertThat(body.getValue()).contains("2").contains("playlists").contains("reassigned")
                .doesNotContain("belong to an administrator");
    }

    @Test
    void deactivatingADesignerRejectsAMissingSuccessorMap() throws Exception {
        User existing = activeDesigner(42L);
        given(userRepository.findById(42L)).willReturn(Optional.of(existing));
        givenOwnedPlaylistsNeedSuccessors();

        mockMvc.perform(post("/admin/users/42/reassign")
                        .param("intent", "deactivate")
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users/42/reassign?intent=deactivate"))
                .andExpect(flash().attribute("flash", Messages.SUCCESSOR_REQUIRED));

        assertThat(existing.getStatus()).isEqualTo(UserStatus.ACTIVE);
        then(mailTransport).should(never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void deactivatingAnAlreadyDeactivatedAccountSendsNothing() throws Exception {
        User existing = activeDesigner(42L);
        existing.setStatus(UserStatus.DEACTIVATED);
        given(userRepository.findById(42L)).willReturn(Optional.of(existing));

        mockMvc.perform(post("/admin/users/42/deactivate")
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flash", Messages.USER_DEACTIVATED));

        then(mailTransport).should(never()).send(anyString(), anyString(), anyString());
        then(playlistService).should(never()).transferOwnedPlaylists(any(), any(), any());
    }

    /** As with Create (E3): the change stands, ADMIN is warned. */
    @Test
    void anUndeliverableDeactivationNoticeLeavesTheAccountDeactivated() throws Exception {
        User existing = activeDesigner(42L);
        given(userRepository.findById(42L)).willReturn(Optional.of(existing));
        willThrow(new MailDeliveryException("smtp is down", new IllegalStateException()))
                .given(mailTransport).send(anyString(), anyString(), anyString());

        mockMvc.perform(post("/admin/users/42/deactivate")
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flash", Messages.USER_DEACTIVATED_EMAIL_FAILED))
                .andExpect(flash().attribute("flashVariant", "warning"));

        assertThat(existing.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
    }

    @Test
    void anAdminCannotDeactivateThemselves() throws Exception {
        User self = new User();
        self.setId(1L);
        self.setEmail("admin@mrs.local");
        self.setRole(Role.ADMIN);
        self.setStatus(UserStatus.ACTIVE);
        given(userRepository.findById(1L)).willReturn(Optional.of(self));

        mockMvc.perform(post("/admin/users/1/deactivate")
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flash", Messages.SELF_MODIFICATION_FORBIDDEN));

        assertThat(self.getStatus()).isEqualTo(UserStatus.ACTIVE);
        then(sessionInvalidationService).should(never()).invalidateSessionsForEmail(anyString());
    }

    @Test
    void reactivatingRestoresAnAccount() throws Exception {
        User existing = activeDesigner(42L);
        existing.setStatus(UserStatus.DEACTIVATED);
        given(userRepository.findById(42L)).willReturn(Optional.of(existing));

        mockMvc.perform(post("/admin/users/42/reactivate")
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flash", Messages.USER_REACTIVATED));

        assertThat(existing.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void changingRoleExpiresSessionsAndRejectsAdminAssignment() throws Exception {
        User existing = activeDesigner(42L);
        given(userRepository.findById(42L)).willReturn(Optional.of(existing));

        mockMvc.perform(post("/admin/users/42/role")
                        .param("role", Role.CUSTOMER.name())
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flash", Messages.USER_ROLE_CHANGED));

        assertThat(existing.getRole()).isEqualTo(Role.CUSTOMER);
        then(sessionInvalidationService).should().invalidateSessionsForEmail("nina@mrs.local");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        then(mailTransport).should().send(eq("nina@mrs.local"),
                eq("Your MRS role has changed"), body.capture());
        assertThat(body.getValue())
                .contains("Nina Designer")
                .contains(Role.CONTENT_DESIGNER.getDisplayName())
                .contains(Role.CUSTOMER.getDisplayName());

        mockMvc.perform(post("/admin/users/42/role")
                        .param("role", Role.ADMIN.name())
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flash",
                        containsString("Content Designer or Customer")));
        assertThat(existing.getRole()).isEqualTo(Role.CUSTOMER);
        // The rejected ADMIN assignment sent nothing further.
        then(mailTransport).should().send(anyString(), anyString(), anyString());
    }

    @Test
    void demotingADesignerAsksForASuccessorPerPlaylist() throws Exception {
        User existing = activeDesigner(42L);
        given(userRepository.findById(42L)).willReturn(Optional.of(existing));
        givenOwnedPlaylistsNeedSuccessors();

        mockMvc.perform(post("/admin/users/42/role")
                        .param("role", Role.CUSTOMER.name())
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users/42/reassign?intent=demote"));

        assertThat(existing.getRole()).isEqualTo(Role.CONTENT_DESIGNER);
        then(mailTransport).should(never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void demotingADesignerTransfersEachPlaylistToTheChosenSuccessor() throws Exception {
        User existing = activeDesigner(42L);
        given(userRepository.findById(42L)).willReturn(Optional.of(existing));
        given(playlistService.transferOwnedPlaylists(eq(42L), eq(Map.of(11L, 15L, 12L, 1L)), eq(1L)))
                .willReturn(2);

        mockMvc.perform(post("/admin/users/42/reassign")
                        .param("intent", "demote")
                        .param("successor[11]", "15")
                        .param("successor[12]", "1")
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flash",
                        Messages.USER_ROLE_CHANGED + " " + Messages.playlistsTransferred(2)));

        assertThat(existing.getRole()).isEqualTo(Role.CUSTOMER);
        then(playlistService).should().transferOwnedPlaylists(eq(42L),
                eq(Map.of(11L, 15L, 12L, 1L)), eq(1L));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        then(mailTransport).should().send(eq("nina@mrs.local"), anyString(), body.capture());
        assertThat(body.getValue()).contains("2").contains("playlists").contains("reassigned")
                .doesNotContain("belong to an administrator");
    }

    @Test
    void promotingACustomerMovesNoPlaylists() throws Exception {
        User existing = activeDesigner(42L);
        existing.setRole(Role.CUSTOMER);
        given(userRepository.findById(42L)).willReturn(Optional.of(existing));

        mockMvc.perform(post("/admin/users/42/role")
                        .param("role", Role.CONTENT_DESIGNER.name())
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flash", Messages.USER_ROLE_CHANGED));

        assertThat(existing.getRole()).isEqualTo(Role.CONTENT_DESIGNER);
        then(playlistService).should(never()).transferOwnedPlaylists(any(), any(), any());
    }

    @Test
    void reassigningTheSameRoleSendsNothing() throws Exception {
        User existing = activeDesigner(42L);
        given(userRepository.findById(42L)).willReturn(Optional.of(existing));

        mockMvc.perform(post("/admin/users/42/role")
                        .param("role", Role.CONTENT_DESIGNER.name())
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flash", Messages.USER_ROLE_CHANGED));

        then(sessionInvalidationService).should(never()).invalidateSessionsForEmail(anyString());
        then(mailTransport).should(never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void theSuccessorPageIsSkippedWhenTheDesignerOwnsNothing() throws Exception {
        given(userRepository.findById(42L)).willReturn(Optional.of(activeDesigner(42L)));
        given(playlistService.successorChoices(42L)).willReturn(List.of());

        mockMvc.perform(get("/admin/users/42/reassign")
                        .param("intent", "deactivate")
                        .with(user(admin())))
                .andExpect(redirectedUrl(Routes.ADMIN_USERS));
    }

    @Test
    void anUndeliverableRoleNoticeLeavesTheNewRoleInPlace() throws Exception {
        User existing = activeDesigner(42L);
        given(userRepository.findById(42L)).willReturn(Optional.of(existing));
        willThrow(new MailDeliveryException("smtp is down", new IllegalStateException()))
                .given(mailTransport).send(anyString(), anyString(), anyString());

        mockMvc.perform(post("/admin/users/42/role")
                        .param("role", Role.CUSTOMER.name())
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flash", Messages.USER_ROLE_CHANGED_EMAIL_FAILED))
                .andExpect(flash().attribute("flashVariant", "warning"));

        assertThat(existing.getRole()).isEqualTo(Role.CUSTOMER);
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

    private void givenOwnedPlaylistsNeedSuccessors() {
        willThrow(new PlaylistSuccessorRequiredException())
                .given(playlistService)
                .transferOwnedPlaylists(eq(42L), eq(Map.of()), eq(1L));
        given(playlistService.successorChoices(42L)).willReturn(List.of(
                new PlaylistSuccessorChoice(11L, "Morning coffee", PlaylistStatus.DRAFT,
                        List.of(new PlaylistOwner(15L, "Dana Designer"))),
                new PlaylistSuccessorChoice(12L, "Evening mix", PlaylistStatus.PUBLISHED,
                        List.of())));
    }
}
