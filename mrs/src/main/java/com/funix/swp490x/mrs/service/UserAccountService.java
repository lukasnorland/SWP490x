package com.funix.swp490x.mrs.service;

import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.InitialPasswordGenerator;
import com.funix.swp490x.mrs.security.PasswordPolicy;
import java.util.List;
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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<User> listAll() {
        return userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /**
     * UC-07: the only path to a new account, since there is no public
     * self-registration (BR-01).
     *
     * @throws InvalidEmailException when the address could not reach a mailbox
     * @throws DuplicateEmailException when the address is already registered
     * @throws WeakPasswordException when the initial password fails BR-12
     */
    @Transactional
    public User create(String name, String email, Role role, String password) {
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
     * Prepares a resend of the credentials message (UC-07 E3).
     *
     * <p>Only the BCrypt hash is stored, so the original password cannot be
     * repeated. Resending therefore issues a fresh one and invalidates what was
     * sent before — which also means a resend whose delivery fails leaves the
     * account reachable only by resending again.
     */
    @Transactional
    public InitialCredentials reissueInitialPassword(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        String password = InitialPasswordGenerator.generate();
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setMustChangePassword(true);
        return new InitialCredentials(userRepository.save(user), password);
    }

    /** An account together with the plain-text password to send it. */
    public record InitialCredentials(User user, String password) {
    }
}
