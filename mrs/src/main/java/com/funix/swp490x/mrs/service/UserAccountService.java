package com.funix.swp490x.mrs.service;

import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.InitialPasswordGenerator;
import com.funix.swp490x.mrs.security.PasswordPolicy;
import com.funix.swp490x.mrs.security.SessionInvalidationService;
import java.util.EnumSet;
import java.util.List;
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
 * <p>Sending the credentials message is deliberately not done here. BR-15 wants
 * the account to survive a failed delivery (UC-07 E3), so the caller sends only
 * once the transaction opened by these methods has committed.
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

    public UserAccountService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SessionInvalidationService sessionInvalidationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionInvalidationService = sessionInvalidationService;
    }

    /**
     * P-06a account list with Zone B filters and Zone D pagination. Newest
     * accounts first so a freshly created row is visible without hunting.
     */
    @Transactional(readOnly = true)
    public Page<User> search(Role role, UserStatus status, String query, int page) {
        String q = query == null || query.isBlank() ? null : query.trim();
        int pageIndex = Math.max(page, 0);
        PageRequest pageable = PageRequest.of(pageIndex, PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return userRepository.search(role, status, q, pageable);
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
    public User create(String name, String email, Role role, String password) {
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
        return userRepository.save(user);
    }

    /**
     * Soft-delete (spec 4.9): accounts are never hard-deleted. Marks the row
     * DEACTIVATED and expires every open session (FT-01 AC-03).
     *
     * @throws SelfModificationException when {@code actorUserId} is the target
     */
    @Transactional
    public User deactivate(Long userId, Long actorUserId) {
        User user = requireUser(userId);
        rejectSelf(user, actorUserId);
        if (user.getStatus() == UserStatus.DEACTIVATED) {
            return user;
        }
        user.setStatus(UserStatus.DEACTIVATED);
        User saved = userRepository.save(user);
        sessionInvalidationService.invalidateSessionsForEmail(saved.getEmail());
        return saved;
    }

    /** Restores a deactivated account so it can authenticate again. */
    @Transactional
    public User reactivate(Long userId) {
        User user = requireUser(userId);
        if (user.getStatus() == UserStatus.ACTIVE) {
            return user;
        }
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    /**
     * UC-06 role change. Only Content Designer and Customer are assignable;
     * ADMIN rows keep their role (they are not created from this screen either).
     *
     * @throws SelfModificationException when {@code actorUserId} is the target
     * @throws InvalidRoleAssignmentException when the new role is not allowed
     */
    @Transactional
    public User changeRole(Long userId, Role newRole, Long actorUserId) {
        requireAssignable(newRole);
        User user = requireUser(userId);
        rejectSelf(user, actorUserId);
        if (user.getRole() == Role.ADMIN) {
            throw new InvalidRoleAssignmentException(
                    "ADMIN accounts keep their role; reassign Content Designer or Customer only.");
        }
        if (user.getRole() == newRole) {
            return user;
        }
        user.setRole(newRole);
        User saved = userRepository.save(user);
        // Authorities live on the session principal — force a fresh login.
        sessionInvalidationService.invalidateSessionsForEmail(saved.getEmail());
        return saved;
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
        return new InitialCredentials(userRepository.save(user), password);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Transactional(readOnly = true)
    public User requireExisting(Long userId) {
        return requireUser(userId);
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

    /** An account together with the plain-text password to send it. */
    public record InitialCredentials(User user, String password) {
    }
}
