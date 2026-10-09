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
 * Admin account management using password-safe view records.
 * Callers send notifications after transaction commit so delivery failure preserves the change (UC-07 E3).
 */
@Service
public class UserAccountService {

    /** Spec 4.9 / Zone D — twenty accounts per page. */
    public static final int PAGE_SIZE = 20;

    /** UC-07 / role-change: ADMIN accounts are not created or assigned from P-06a. */
    public static final Set<Role> ASSIGNABLE_ROLES =
            EnumSet.of(Role.CONTENT_DESIGNER, Role.CUSTOMER);

    /** {@code users.username} is VARCHAR(100). */
    public static final int NAME_MAX_LENGTH = 100;

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

    /** Pages Content Designer and Customer accounts newest first; ADMIN accounts are excluded. */
    @Transactional(readOnly = true)
    public Page<UserView> search(Role role, UserStatus status, String query, int page) {
        String q = query == null || query.isBlank() ? null : query.trim();
        int pageIndex = Math.max(page, 0);
        PageRequest pageable = PageRequest.of(pageIndex, PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return userRepository.search(role, status, q, pageable).map(UserView::of);
    }

    /**
     * Creates an account through ADMIN; public requests do not create accounts (BR-01, BR-19).
     * @throws InvalidEmailException when email syntax or length is invalid
     * @throws DuplicateEmailException when the email is registered
     * @throws WeakPasswordException when the password fails BR-12
     * @throws InvalidRoleAssignmentException when the role is not assignable
     */
    @Transactional
    public UserView create(String name, String email, Role role, String password, Long actorId) {
        requireAssignable(role);

        String address = validateNewEmail(email);

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

    /** Validate before recipient preparation; create repeats this check before writing. */
    @Transactional(readOnly = true)
    public String validateNewEmail(String email) {
        return validateEmailFor(null, email);
    }

    /**
     * As {@link #validateNewEmail}, except the address may already belong to
     * {@code userId} itself — the edit dialog submits the current address
     * when only the name or role changes.
     */
    @Transactional(readOnly = true)
    public String validateEmailFor(Long userId, String email) {
        String address = email == null ? "" : email.trim();
        if (!EmailPolicy.isWellFormed(address)) {
            throw new InvalidEmailException(address);
        }
        userRepository.findByEmail(address)
                .filter(owner -> userId == null || !userId.equals(owner.getId()))
                .ifPresent(owner -> {
                    throw new DuplicateEmailException(address);
                });
        return address;
    }

    /**
     * ADMIN edit of name, email and role. Only fields that differ are written;
     * the password is untouched. Any change expires the holder's sessions so
     * the next request signs them out and they sign in with the current
     * password. SES verification of a new address is the caller's job.
     *
     * @throws SelfModificationException when the actor is the target
     * @throws InvalidRoleAssignmentException when the target is ADMIN or the role is not assignable
     * @throws InvalidNameException when the name is blank or too long
     * @throws InvalidEmailException when email syntax or length is invalid
     * @throws DuplicateEmailException when another account holds the email
     * @throws PlaylistSuccessorRequiredException when a demotion needs successors; nothing is written
     */
    @Transactional
    public AccountUpdate update(Long userId, String name, String email, Role role,
            Long actorUserId) {
        User user = requireUser(userId);
        rejectSelf(user, actorUserId);
        if (user.getRole() == Role.ADMIN) {
            throw new InvalidRoleAssignmentException(
                    "ADMIN accounts are not edited from User Management.");
        }
        String newName = name == null ? "" : name.trim();
        if (newName.isEmpty() || newName.length() > NAME_MAX_LENGTH) {
            throw new InvalidNameException(newName);
        }
        String newEmail = validateEmailFor(userId, email);
        String previousName = user.getUsername();
        String previousEmail = user.getEmail();

        // Role first: a demotion that still needs successors throws before
        // the name or email is touched.
        RoleChange roleChange = changeRole(userId, role, actorUserId);

        boolean nameChanged = !newName.equals(previousName);
        boolean emailChanged = !newEmail.equals(previousEmail);
        if (nameChanged || emailChanged) {
            user.setUsername(newName);
            user.setEmail(newEmail);
            User saved = userRepository.save(user);
            // Registry principals are keyed by the address they signed in with.
            sessionInvalidationService.invalidateSessionsForEmail(previousEmail);
            audit(actorUserId, AuditLog.ACTION_USER_UPDATE, saved.getId(),
                    userDetails(saved, "\"before\":{\"email\":%s,\"name\":%s}"
                            .formatted(jsonString(previousEmail), jsonString(previousName))));
        }
        return new AccountUpdate(UserView.of(user), previousName, previousEmail,
                roleChange.previousRole(), roleChange.transferredPlaylists());
    }

    /**
     * Deactivates the account and revokes sessions after playlist succession (UC-08, BR-14).
     * Accounts are never hard-deleted.
     *
     * @throws SelfModificationException when the actor is the target
     * @throws PlaylistSuccessorRequiredException when a required successor is missing
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
     * Changes a managed role; Designer demotion transfers owned playlists to selected successors (BR-14).
     * @throws SelfModificationException when the actor is the target
     * @throws InvalidRoleAssignmentException when the role is not allowed
     * @throws PlaylistSuccessorRequiredException when required successors are missing
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
     * Replaces the hashed password with a fresh initial password for resend (UC-07 E3).
     * The original cannot be recovered; a failed delivery can be retried or followed by password reset.
     * @throws SelfModificationException when the actor is the target
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

    /**
     * Outcome of {@link #update}: the account as it now stands and what each
     * edited field held before, so callers can tell the holder what moved.
     */
    public record AccountUpdate(UserView user, String previousName, String previousEmail,
            Role previousRole, int transferredPlaylists) {

        public boolean nameChanged() {
            return !user.username().equals(previousName);
        }

        public boolean emailChanged() {
            return !user.email().equals(previousEmail);
        }

        public boolean roleChanged() {
            return previousRole != user.role();
        }

        public boolean changed() {
            return nameChanged() || emailChanged() || roleChanged();
        }
    }
}
