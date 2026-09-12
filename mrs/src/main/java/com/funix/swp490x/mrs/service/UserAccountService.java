package com.funix.swp490x.mrs.service;

import com.funix.swp490x.mrs.domain.AuditLog;
import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.InitialPasswordGenerator;
import com.funix.swp490x.mrs.security.PasswordPolicy;
import com.funix.swp490x.mrs.security.SessionInvalidationService;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account administration for P-06a (UC-06, UC-07).
 *
 * <p>Every public method returns a {@link UserView}, never the {@link User}
 * entity (TDS Part 2.5). Sending the credentials message is deliberately not
 * done here. BR-15 wants the account to survive a failed delivery (UC-07 E3),
 * so the caller sends only once the transaction opened by these methods has
 * committed.
 */
@Service
public class UserAccountService {

    /** Spec 4.9 / Zone D — twenty accounts per page. */
    public static final int PAGE_SIZE = 20;

    /** UC-07 / role-change: ADMIN accounts are not created or assigned from P-06a. */
    public static final Set<Role> ASSIGNABLE_ROLES =
            EnumSet.of(Role.CONTENT_DESIGNER, Role.CUSTOMER);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionInvalidationService sessionInvalidationService;
    private final PlaylistService playlistService;
    private final AuditLogRepository auditLogRepository;

    public UserAccountService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SessionInvalidationService sessionInvalidationService,
            PlaylistService playlistService,
            AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionInvalidationService = sessionInvalidationService;
        this.playlistService = playlistService;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * P-06a account list with Zone B filters and Zone D pagination. Newest
     * accounts first so a freshly created row is visible without hunting.
     * ADMIN accounts are omitted — P-06a manages Content Designers and
     * Customers only.
     */
    @Transactional(readOnly = true)
    public Page<UserView> search(Role role, UserStatus status, String query, int page) {
        String q = query == null || query.isBlank() ? null : query.trim();
        int pageIndex = Math.max(page, 0);
        PageRequest pageable = PageRequest.of(pageIndex, PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return userRepository.search(role, status, q, pageable).map(UserView::of);
    }

    /**
     * UC-07: the only path to a new account, since there is no public
     * self-registration (BR-01).
     *
     * @throws InvalidEmailException when the address could not reach a mailbox
     * @throws DuplicateEmailException when the address is already registered
     * @throws WeakPasswordException when the initial password fails BR-12
     * @throws InvalidRoleAssignmentException when the role is not assignable
     */
    @Transactional
    public UserView create(String name, String email, Role role, String password, Long actorId) {
        requireAssignable(role);

        String address = email == null ? "" : email.trim();
        if (!EmailPolicy.isWellFormed(address)) {
            throw new InvalidEmailException(address);
        }
        if (userRepository.findByEmail(address).isPresent()) {
            throw new DuplicateEmailException(address);
        }

        List<String> violations = PasswordPolicy.violations(password);
        if (!violations.isEmpty()) {
            throw new WeakPasswordException(violations);
        }

        User user = new User();
        user.setUsername(name == null ? "" : name.trim());
        user.setEmail(address);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(true);
        User saved = userRepository.save(user);
        audit(actorId, AuditLog.ACTION_USER_CREATE, saved.getId(),
                userDetails(saved, "\"role\":%s".formatted(jsonString(saved.getRole().name()))));
        return UserView.of(saved);
    }

    /**
     * Soft-delete (spec 4.9): accounts are never hard-deleted. Marks the row
     * DEACTIVATED and expires every open session (FT-01 AC-03). Playlists the
     * account owns go to the successor named for each one (a collaborator, or
     * the acting ADMIN).
     *
     * @throws SelfModificationException when {@code actorUserId} is the target
     * @throws PlaylistSuccessorRequiredException when they own playlists and
     *     {@code successors} does not cover every one
     */
    @Transactional
    public Deactivation deactivate(Long userId, Long actorUserId) {
        return deactivate(userId, actorUserId, Map.of());
    }

    @Transactional
    public Deactivation deactivate(Long userId, Long actorUserId, Map<Long, Long> successors) {
        User user = requireUser(userId);
        rejectSelf(user, actorUserId);
        if (user.getStatus() == UserStatus.DEACTIVATED) {
            return new Deactivation(UserView.of(user), false, 0);
        }
        int transferred = reassignOwnedPlaylists(user.getId(), actorUserId, successors);
        user.setStatus(UserStatus.DEACTIVATED);
        User saved = userRepository.save(user);
        sessionInvalidationService.invalidateSessionsForEmail(saved.getEmail());
        audit(actorUserId, AuditLog.ACTION_USER_DEACTIVATE, saved.getId(),
                userDetails(saved, "\"before\":%s,\"after\":%s,\"transferredPlaylists\":%d"
                        .formatted(jsonString(UserStatus.ACTIVE.name()),
                                jsonString(UserStatus.DEACTIVATED.name()), transferred)));
        return new Deactivation(UserView.of(saved), true, transferred);
    }

    /** Restores a deactivated account so it can authenticate again. */
    @Transactional
    public UserView reactivate(Long userId, Long actorUserId) {
        User user = requireUser(userId);
        if (user.getStatus() == UserStatus.ACTIVE) {
            return UserView.of(user);
        }
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.save(user);
        audit(actorUserId, AuditLog.ACTION_USER_REACTIVATE, saved.getId(),
                userDetails(saved, "\"before\":%s,\"after\":%s"
                        .formatted(jsonString(UserStatus.DEACTIVATED.name()),
                                jsonString(UserStatus.ACTIVE.name()))));
        return UserView.of(saved);
    }

    /**
     * UC-06 role change. Only Content Designer and Customer are assignable;
     * ADMIN rows keep their role (they are not created from this screen either).
     *
     * <p>Demoting a Content Designer to Customer reassigns every playlist they
     * own to the successor named for each one, since a Customer can neither
     * edit nor unpublish.
     *
     * @throws SelfModificationException when {@code actorUserId} is the target
     * @throws InvalidRoleAssignmentException when the new role is not allowed
     * @throws PlaylistSuccessorRequiredException when they own playlists and
     *     {@code successors} does not cover every one
     */
    @Transactional
    public RoleChange changeRole(Long userId, Role newRole, Long actorUserId) {
        return changeRole(userId, newRole, actorUserId, Map.of());
    }

    @Transactional
    public RoleChange changeRole(Long userId, Role newRole, Long actorUserId,
            Map<Long, Long> successors) {
        requireAssignable(newRole);
        User user = requireUser(userId);
        rejectSelf(user, actorUserId);
        if (user.getRole() == Role.ADMIN) {
            throw new InvalidRoleAssignmentException(
                    "ADMIN accounts keep their role; reassign Content Designer or Customer only.");
        }
        Role previousRole = user.getRole();
        if (previousRole == newRole) {
            return new RoleChange(UserView.of(user), previousRole, 0);
        }
        int transferred = 0;
        if (previousRole == Role.CONTENT_DESIGNER && newRole == Role.CUSTOMER
                && actorUserId != null) {
            transferred = reassignOwnedPlaylists(user.getId(), actorUserId, successors);
        }
        user.setRole(newRole);
        User saved = userRepository.save(user);
        // Authorities live on the session principal — force a fresh login.
        sessionInvalidationService.invalidateSessionsForEmail(saved.getEmail());
        audit(actorUserId, AuditLog.ACTION_USER_ROLE_CHANGE, saved.getId(),
                userDetails(saved, "\"before\":%s,\"after\":%s,\"transferredPlaylists\":%d"
                        .formatted(jsonString(previousRole.name()), jsonString(newRole.name()),
                                transferred)));
        return new RoleChange(UserView.of(saved), previousRole, transferred);
    }

    public boolean ownsPlaylists(Long userId) {
        return playlistService.ownsPlaylists(userId);
    }

    public List<PlaylistSuccessorChoice> successorChoices(Long userId) {
        return playlistService.successorChoices(userId);
    }

    /**
     * Reassigns owned playlists, or only drops the leaving user's collaborator
     * grants when they owned none. Throws before the account changes so ADMIN
     * can pick successors.
     */
    private int reassignOwnedPlaylists(Long fromUserId, Long actorUserId,
            Map<Long, Long> successors) {
        if (actorUserId == null) {
            return 0;
        }
        return playlistService.transferOwnedPlaylists(fromUserId, successors, actorUserId);
    }

    @Transactional(readOnly = true)
    public UserView get(Long userId) {
        return UserView.of(requireUser(userId));
    }

    /**
     * Prepares a resend of the credentials message (UC-07 E3).
     *
     * <p>Only the BCrypt hash is stored, so the original password cannot be
     * repeated. Resending therefore issues a fresh one and invalidates what was
     * sent before — which also means a resend whose delivery fails leaves the
     * account reachable only by resending again.
     *
     * @throws SelfModificationException when {@code actorUserId} is the target
     */
    @Transactional
    public InitialCredentials reissueInitialPassword(Long userId, Long actorUserId) {
        User user = requireUser(userId);
        rejectSelf(user, actorUserId);
        String password = InitialPasswordGenerator.generate();
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setMustChangePassword(true);
        User saved = userRepository.save(user);
        audit(actorUserId, AuditLog.ACTION_USER_CREDENTIALS_RESEND, saved.getId(),
                userDetails(saved, null));
        return new InitialCredentials(UserView.of(saved), password);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private static void requireAssignable(Role role) {
        if (role == null || !ASSIGNABLE_ROLES.contains(role)) {
            throw new InvalidRoleAssignmentException(
                    "New accounts can be assigned the Content Designer or Customer role only.");
        }
    }

    private static void rejectSelf(User user, Long actorUserId) {
        if (actorUserId != null && actorUserId.equals(user.getId())) {
            throw new SelfModificationException();
        }
    }

    private void audit(Long actorId, String action, Long entityId, String details) {
        if (actorId == null || entityId == null) {
            return;
        }
        auditLogRepository.save(new AuditLog(actorId, action, AuditLog.ENTITY_USER, entityId, details));
    }

    private static String userDetails(User user, String extra) {
        String base = "\"email\":%s,\"name\":%s".formatted(
                jsonString(user.getEmail()), jsonString(user.getUsername()));
        if (extra == null || extra.isBlank()) {
            return "{" + base + "}";
        }
        return "{" + base + "," + extra + "}";
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** An account together with the plain-text password to send it. */
    public record InitialCredentials(UserView user, String password) {
    }

    /**
     * Outcome of {@link #deactivate}. {@code changed} is false when the account
     * was already deactivated, so callers can skip notifying the holder twice;
     * {@code transferredPlaylists} is how many playlists were reassigned.
     */
    public record Deactivation(UserView user, boolean changed, int transferredPlaylists) {
    }

    /**
     * Outcome of {@link #changeRole}: the account as it now stands, the role it
     * held before, and how many playlists were reassigned (non-zero only for a
     * Content Designer demoted to Customer).
     */
    public record RoleChange(UserView user, Role previousRole, int transferredPlaylists) {
        public boolean changed() {
            return previousRole != user.role();
        }
    }
}
