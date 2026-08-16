package com.funix.swp490x.mrs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.mail.MailDeliveryException;
import com.funix.swp490x.mrs.mail.NotificationService;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.PasswordResetTokenService;
import com.funix.swp490x.mrs.service.AuthService.AccountRequestStatus;
import com.funix.swp490x.mrs.service.AuthService.PasswordChangeResult;
import com.funix.swp490x.mrs.service.AuthService.PasswordResetResult;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "designer@mrs.local";
    private static final String STRONG = "FreshPass@2026";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenService tokenService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, tokenService, notificationService,
                passwordEncoder);
    }

    @Test
    void requestAccount_whenEmailIsMalformed_shouldRejectWithoutSending() {
        var result = authService.requestAccount("not-an-email");

        assertThat(result.status()).isEqualTo(AccountRequestStatus.INVALID_EMAIL);
        then(notificationService).should(never()).sendRegistrationRequest(anyString());
    }

    @Test
    void requestAccount_whenMailFails_shouldReportMailFailed() {
        willThrow(new MailDeliveryException("smtp is down", new IllegalStateException()))
                .given(notificationService).sendRegistrationRequest(EMAIL);

        var result = authService.requestAccount(EMAIL);

        assertThat(result.status()).isEqualTo(AccountRequestStatus.MAIL_FAILED);
        assertThat(result.email()).isEqualTo(EMAIL);
    }

    @Test
    void requestPasswordReset_whenAddressIsUnknown_shouldSendNothing() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        authService.requestPasswordReset(EMAIL);

        then(tokenService).shouldHaveNoInteractions();
        then(notificationService).should(never()).sendPasswordResetLink(anyString(), anyString());
    }

    @Test
    void requestPasswordReset_whenAddressIsRegistered_shouldIssueALink() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(designer()));
        given(tokenService.issue(EMAIL)).willReturn("reset-token");

        authService.requestPasswordReset(EMAIL);

        then(notificationService).should().sendPasswordResetLink(EMAIL, "reset-token");
    }

    @Test
    void completePasswordReset_whenTokenIsExpired_shouldRejectWithoutWriting() {
        given(tokenService.emailFor("gone")).willReturn(Optional.empty());

        PasswordResetResult result = authService.completePasswordReset("gone", STRONG, STRONG);

        assertThat(result.expired()).isTrue();
        then(userRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void completePasswordReset_whenPasswordIsWeak_shouldKeepTheTokenUsable() {
        given(tokenService.emailFor("token")).willReturn(Optional.of(EMAIL));

        PasswordResetResult result = authService.completePasswordReset("token", "weak", "weak");

        assertThat(result.succeeded()).isFalse();
        assertThat(result.violations()).isNotEmpty();
        then(tokenService).should(never()).invalidate(anyString());
        then(userRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void changePassword_whenCurrentPasswordIsWrong_shouldStayOnTheScreen() {
        User user = designer();
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", user.getPasswordHash())).willReturn(false);

        PasswordChangeResult result = authService.changePassword(EMAIL, "wrong", STRONG, STRONG);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.violations()).contains("Your current password is incorrect");
        then(userRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void changePassword_whenPolicyHolds_shouldClearTheForcedChangeFlag() {
        User user = designer();
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("Admin@2026", user.getPasswordHash())).willReturn(true);
        given(passwordEncoder.encode(STRONG)).willReturn("{bcrypt}new");
        given(userRepository.save(user)).willReturn(user);

        PasswordChangeResult result = authService.changePassword(EMAIL, "Admin@2026", STRONG, STRONG);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.landingPath()).isEqualTo(Role.CONTENT_DESIGNER.getLandingPath());
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}new");
    }

    private static User designer() {
        User user = new User();
        user.setId(2L);
        user.setUsername("Nina Designer");
        user.setEmail(EMAIL);
        user.setPasswordHash("{bcrypt}old");
        user.setRole(Role.CONTENT_DESIGNER);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(true);
        return user;
    }
}
