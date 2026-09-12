package com.funix.swp490x.mrs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.funix.swp490x.mrs.domain.AuditLog;
import com.funix.swp490x.mrs.domain.PlaylistStatus;
import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.PasswordPolicy;
import com.funix.swp490x.mrs.security.SessionInvalidationService;
import com.funix.swp490x.mrs.service.UserAccountService.Deactivation;
import com.funix.swp490x.mrs.service.UserAccountService.InitialCredentials;
import com.funix.swp490x.mrs.service.UserAccountService.RoleChange;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    private static final String EMAIL = "nina@mrs.local";
    private static final String STRONG = "InitPass@1";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SessionInvalidationService sessionInvalidationService;
    @Mock
    private PlaylistService playlistService;
    @Mock
    private AuditLogRepository auditLogRepository;

    private UserAccountService service;

    @BeforeEach
    void setUp() {
        service = new UserAccountService(userRepository, passwordEncoder,
                sessionInvalidationService, playlistService, auditLogRepository);
        org.mockito.Mockito.lenient().when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User saved = invocation.getArgument(0);
                    if (saved.getId() == null) {
                        saved.setId(42L);
                    }
                    return saved;
                });
    }

    @Test
    void search_whenFiltersGiven_shouldBindThemAndTrimQuery() {
        given(userRepository.search(any(), any(), any(), any())).willReturn(Page.empty());

        service.search(Role.CONTENT_DESIGNER, UserStatus.ACTIVE, "  nina  ", 0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        then(userRepository).should().search(eq(Role.CONTENT_DESIGNER), eq(UserStatus.ACTIVE),
                eq("nina"), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(UserAccountService.PAGE_SIZE);
    }

    @Test
    void search_whenQueryIsBlank_shouldPassNull() {
        given(userRepository.search(any(), any(), any(), any())).willReturn(Page.empty());

        service.search(null, null, "   ", 0);

        then(userRepository).should().search(isNull(), isNull(), isNull(), any());
    }

    @Test
    void create_whenValid_shouldSaveActiveAccountWithForcedChange() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());
        given(passwordEncoder.encode(STRONG)).willReturn("{bcrypt}hash");

        UserView saved = service.create("Nina Designer", EMAIL, Role.CONTENT_DESIGNER, STRONG, 1L);

        assertThat(saved.email()).isEqualTo(EMAIL);
        assertThat(saved.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.mustChangePassword()).isTrue();
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("{bcrypt}hash");
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo(STRONG);
        then(auditLogRepository).should().save(any(AuditLog.class));
    }

    @Test
    void create_whenEmailIsMalformed_shouldThrowInvalidEmail() {
        assertThatThrownBy(() -> service.create("Nina", "not-an-email", Role.CUSTOMER, STRONG, 1L))
                .isInstanceOf(InvalidEmailException.class);

        then(userRepository).should(never()).save(any());
    }

    @Test
    void create_whenEmailAlreadyRegistered_shouldThrowDuplicateEmail() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(account(7L,
                Role.CONTENT_DESIGNER, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.create("Other", "  " + EMAIL + "  ", Role.CUSTOMER, STRONG, 1L))
                .isInstanceOf(DuplicateEmailException.class);

        then(userRepository).should(never()).save(any());
    }

    @Test
    void create_whenPasswordIsWeak_shouldThrowWeakPassword() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("Nina", EMAIL, Role.CUSTOMER, "Ab1!xyZ", 1L))
                .isInstanceOf(WeakPasswordException.class);

        then(userRepository).should(never()).save(any());
    }

    @Test
    void create_whenRoleIsAdmin_shouldThrowInvalidRoleAssignment() {
        assertThatThrownBy(() -> service.create("Root", EMAIL, Role.ADMIN, STRONG, 1L))
                .isInstanceOf(InvalidRoleAssignmentException.class);

        then(userRepository).should(never()).save(any());
    }

    @Test
    void create_whenEmailHasSpaces_shouldTrim() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());
        given(passwordEncoder.encode(STRONG)).willReturn("{bcrypt}hash");

        UserView saved = service.create("Nina", "  " + EMAIL + "  ", Role.CUSTOMER, STRONG, 1L);

        assertThat(saved.email()).isEqualTo(EMAIL);
        then(userRepository).should().findByEmail(EMAIL);
    }

    @Test
    void deactivate_whenNoOwnedPlaylists_shouldDeactivateAndInvalidateSessions() {
        User user = account(7L, Role.CONTENT_DESIGNER, UserStatus.ACTIVE);
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        given(playlistService.transferOwnedPlaylists(eq(7L), any(), eq(1L))).willReturn(0);

        Deactivation result = service.deactivate(7L, 1L);

        assertThat(result.changed()).isTrue();
        assertThat(result.transferredPlaylists()).isZero();
        assertThat(result.user().status()).isEqualTo(UserStatus.DEACTIVATED);
        then(sessionInvalidationService).should().invalidateSessionsForEmail(EMAIL);
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        then(auditLogRepository).should().save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(AuditLog.ACTION_USER_DEACTIVATE);
        assertThat(audit.getValue().getDetails()).contains(UserStatus.ACTIVE.name())
                .contains(UserStatus.DEACTIVATED.name());
    }

    @Test
    void deactivate_whenSuccessorsValid_shouldTransferEveryPlaylist() {
        User user = account(7L, Role.CONTENT_DESIGNER, UserStatus.ACTIVE);
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        Map<Long, Long> successors = Map.of(11L, 15L, 12L, 1L);
        given(playlistService.transferOwnedPlaylists(7L, successors, 1L)).willReturn(2);

        Deactivation result = service.deactivate(7L, 1L, successors);

        assertThat(result.transferredPlaylists()).isEqualTo(2);
        assertThat(result.user().status()).isEqualTo(UserStatus.DEACTIVATED);
    }

    @Test
    void deactivate_whenASuccessorIsMissing_shouldThrowAndChangeNothing() {
        User user = account(7L, Role.CONTENT_DESIGNER, UserStatus.ACTIVE);
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        willThrow(new PlaylistSuccessorRequiredException())
                .given(playlistService).transferOwnedPlaylists(eq(7L), any(), eq(1L));

        assertThatThrownBy(() -> service.deactivate(7L, 1L, Map.of()))
                .isInstanceOf(PlaylistSuccessorRequiredException.class);

        then(userRepository).should(never()).save(any());
        then(sessionInvalidationService).shouldHaveNoInteractions();
    }

    @Test
    void deactivate_whenSuccessorIsAStranger_shouldThrowInvalidSuccessor() {
        User user = account(7L, Role.CONTENT_DESIGNER, UserStatus.ACTIVE);
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        willThrow(new InvalidSuccessorException("not allowed"))
                .given(playlistService).transferOwnedPlaylists(eq(7L), any(), eq(1L));

        assertThatThrownBy(() -> service.deactivate(7L, 1L, Map.of(11L, 99L)))
                .isInstanceOf(InvalidSuccessorException.class);

        then(userRepository).should(never()).save(any());
    }

    @Test
    void deactivate_whenTargetIsSelf_shouldThrowSelfModification() {
        User user = account(1L, Role.ADMIN, UserStatus.ACTIVE);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.deactivate(1L, 1L))
                .isInstanceOf(SelfModificationException.class);

        then(userRepository).should(never()).save(any());
    }

    @Test
    void deactivate_whenAlreadyDeactivated_shouldBeIdempotent() {
        User user = account(7L, Role.CONTENT_DESIGNER, UserStatus.DEACTIVATED);
        given(userRepository.findById(7L)).willReturn(Optional.of(user));

        Deactivation result = service.deactivate(7L, 1L);

        assertThat(result.changed()).isFalse();
        then(userRepository).should(never()).save(any());
        then(playlistService).shouldHaveNoInteractions();
    }

    @Test
    void deactivate_whenUserUnknown_shouldThrowNotFound() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(99L, 1L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void reactivate_whenDeactivated_shouldSetActive() {
        User user = account(7L, Role.CUSTOMER, UserStatus.DEACTIVATED);
        given(userRepository.findById(7L)).willReturn(Optional.of(user));

        UserView view = service.reactivate(7L, 1L);

        assertThat(view.status()).isEqualTo(UserStatus.ACTIVE);
        then(userRepository).should().save(user);
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        then(auditLogRepository).should().save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(AuditLog.ACTION_USER_REACTIVATE);
    }

    @Test
    void reactivate_whenAlreadyActive_shouldNotSave() {
        User user = account(7L, Role.CUSTOMER, UserStatus.ACTIVE);
        given(userRepository.findById(7L)).willReturn(Optional.of(user));

        UserView view = service.reactivate(7L, 1L);

        assertThat(view.status()).isEqualTo(UserStatus.ACTIVE);
        then(userRepository).should(never()).save(any());
        then(auditLogRepository).shouldHaveNoInteractions();
    }

    @Test
    void reactivate_whenUserUnknown_shouldThrowNotFound() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.reactivate(99L, 1L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void changeRole_whenCustomerPromoted_shouldChangeRoleAndInvalidateSessions() {
        User user = account(7L, Role.CUSTOMER, UserStatus.ACTIVE);
        given(userRepository.findById(7L)).willReturn(Optional.of(user));

        RoleChange result = service.changeRole(7L, Role.CONTENT_DESIGNER, 1L);

        assertThat(result.previousRole()).isEqualTo(Role.CUSTOMER);
        assertThat(result.user().role()).isEqualTo(Role.CONTENT_DESIGNER);
        assertThat(result.changed()).isTrue();
        then(sessionInvalidationService).should().invalidateSessionsForEmail(EMAIL);
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        then(auditLogRepository).should().save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(AuditLog.ACTION_USER_ROLE_CHANGE);
        assertThat(audit.getValue().getDetails()).contains(Role.CUSTOMER.name())
                .contains(Role.CONTENT_DESIGNER.name());
    }

    @Test
    void changeRole_whenDemotedWithSuccessors_shouldTransferPlaylists() {
        User user = account(7L, Role.CONTENT_DESIGNER, UserStatus.ACTIVE);
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        Map<Long, Long> successors = Map.of(11L, 15L);
        given(playlistService.transferOwnedPlaylists(7L, successors, 1L)).willReturn(1);

        RoleChange result = service.changeRole(7L, Role.CUSTOMER, 1L, successors);

        assertThat(result.transferredPlaylists()).isEqualTo(1);
        assertThat(result.user().role()).isEqualTo(Role.CUSTOMER);
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        then(auditLogRepository).should().save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(AuditLog.ACTION_USER_ROLE_CHANGE);
        assertThat(audit.getValue().getDetails()).contains("\"transferredPlaylists\":1");
    }

    @Test
    void changeRole_whenDemotedWithoutSuccessor_shouldThrow() {
        User user = account(7L, Role.CONTENT_DESIGNER, UserStatus.ACTIVE);
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        willThrow(new PlaylistSuccessorRequiredException())
                .given(playlistService).transferOwnedPlaylists(eq(7L), any(), eq(1L));

        assertThatThrownBy(() -> service.changeRole(7L, Role.CUSTOMER, 1L, Map.of()))
                .isInstanceOf(PlaylistSuccessorRequiredException.class);

        then(userRepository).should(never()).save(any());
    }

    @Test
    void changeRole_whenTargetIsAdmin_shouldThrowInvalidRoleAssignment() {
        User user = account(1L, Role.ADMIN, UserStatus.ACTIVE);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.changeRole(1L, Role.CONTENT_DESIGNER, 99L))
                .isInstanceOf(InvalidRoleAssignmentException.class);

        then(userRepository).should(never()).save(any());
    }

    @Test
    void changeRole_whenNewRoleNotAssignable_shouldThrow() {
        assertThatThrownBy(() -> service.changeRole(7L, Role.ADMIN, 1L))
                .isInstanceOf(InvalidRoleAssignmentException.class);
        assertThatThrownBy(() -> service.changeRole(7L, null, 1L))
                .isInstanceOf(InvalidRoleAssignmentException.class);

        then(userRepository).should(never()).save(any());
    }

    @Test
    void changeRole_whenRoleUnchanged_shouldBeNoOp() {
        User user = account(7L, Role.CUSTOMER, UserStatus.ACTIVE);
        given(userRepository.findById(7L)).willReturn(Optional.of(user));

        RoleChange result = service.changeRole(7L, Role.CUSTOMER, 1L);

        assertThat(result.changed()).isFalse();
        then(userRepository).should(never()).save(any());
        then(sessionInvalidationService).shouldHaveNoInteractions();
    }

    @Test
    void changeRole_whenTargetIsSelf_shouldThrowSelfModification() {
        User user = account(1L, Role.CONTENT_DESIGNER, UserStatus.ACTIVE);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.changeRole(1L, Role.CUSTOMER, 1L))
                .isInstanceOf(SelfModificationException.class);
    }

    @Test
    void reissueInitialPassword_whenUserExists_shouldReplaceHashAndForceChange() {
        User user = account(7L, Role.CUSTOMER, UserStatus.ACTIVE);
        user.setPasswordHash("{bcrypt}old");
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        given(passwordEncoder.encode(any())).willReturn("{bcrypt}new");

        InitialCredentials result = service.reissueInitialPassword(7L, 1L);

        assertThat(result.password()).isNotBlank();
        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}new");
        assertThat(user.isMustChangePassword()).isTrue();
        assertThat(result.user().mustChangePassword()).isTrue();
    }

    @Test
    void reissueInitialPassword_generatedPassword_shouldSatisfyPolicy() {
        User user = account(7L, Role.CUSTOMER, UserStatus.ACTIVE);
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        given(passwordEncoder.encode(any())).willAnswer(invocation -> invocation.getArgument(0));

        InitialCredentials result = service.reissueInitialPassword(7L, 1L);

        assertThat(PasswordPolicy.isValid(result.password())).isTrue();
        assertThat(result.password().length()).isBetween(8, 64);
    }

    @Test
    void reissueInitialPassword_whenTargetIsSelf_shouldThrow() {
        User user = account(1L, Role.ADMIN, UserStatus.ACTIVE);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.reissueInitialPassword(1L, 1L))
                .isInstanceOf(SelfModificationException.class);

        then(userRepository).should(never()).save(any());
    }

    @Test
    void reissueInitialPassword_whenUserUnknown_shouldThrowNotFound() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.reissueInitialPassword(99L, 1L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void successorChoices_whenUserOwnsPlaylists_shouldDelegate() {
        List<PlaylistSuccessorChoice> choices = List.of(new PlaylistSuccessorChoice(
                11L, "Morning", PlaylistStatus.DRAFT, List.of(new PlaylistOwner(15L, "Dana"))));
        given(playlistService.successorChoices(7L)).willReturn(choices);

        assertThat(service.successorChoices(7L)).isEqualTo(choices);
        then(playlistService).should().successorChoices(7L);
    }

    @Test
    void create_shouldWriteUserCreateAuditWithoutThePassword() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());
        given(passwordEncoder.encode(STRONG)).willReturn("{bcrypt}hash");

        service.create("Nina Designer", EMAIL, Role.CONTENT_DESIGNER, STRONG, 1L);

        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        then(auditLogRepository).should().save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(AuditLog.ACTION_USER_CREATE);
        assertThat(audit.getValue().getEntityType()).isEqualTo(AuditLog.ENTITY_USER);
        assertThat(audit.getValue().getActorId()).isEqualTo(1L);
        assertThat(audit.getValue().getDetails()).contains(EMAIL).doesNotContain(STRONG);
    }

    @Test
    void deactivate_whenAlreadyDeactivated_shouldNotAudit() {
        User user = account(7L, Role.CUSTOMER, UserStatus.DEACTIVATED);
        given(userRepository.findById(7L)).willReturn(Optional.of(user));

        service.deactivate(7L, 1L);

        then(auditLogRepository).shouldHaveNoInteractions();
    }

    @Test
    void changeRole_whenUnchanged_shouldNotAudit() {
        User user = account(7L, Role.CUSTOMER, UserStatus.ACTIVE);
        given(userRepository.findById(7L)).willReturn(Optional.of(user));

        service.changeRole(7L, Role.CUSTOMER, 1L);

        then(auditLogRepository).shouldHaveNoInteractions();
    }

    @Test
    void reissueInitialPassword_shouldAuditWithoutTheNewPassword() {
        User user = account(7L, Role.CUSTOMER, UserStatus.ACTIVE);
        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        given(passwordEncoder.encode(any())).willReturn("{bcrypt}new");

        InitialCredentials result = service.reissueInitialPassword(7L, 1L);

        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        then(auditLogRepository).should().save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(AuditLog.ACTION_USER_CREDENTIALS_RESEND);
        assertThat(audit.getValue().getDetails()).doesNotContain(result.password());
    }

    @Test
    void successorChoices_whenNoPlaylists_shouldBeEmpty() {
        given(playlistService.successorChoices(7L)).willReturn(List.of());

        assertThat(service.successorChoices(7L)).isEmpty();
    }

    private static User account(Long id, Role role, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setUsername("Nina Designer");
        user.setEmail(EMAIL);
        user.setPasswordHash("{bcrypt}old");
        user.setRole(role);
        user.setStatus(status);
        user.setMustChangePassword(false);
        return user;
    }
}
