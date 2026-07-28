package com.funix.swp490x.mrs.security;

import com.funix.swp490x.mrs.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MrsUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final LoginAttemptService loginAttemptService;

    public MrsUserDetailsService(UserRepository userRepository, LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.loginAttemptService = loginAttemptService;
    }

    /**
     * The message is deliberately generic: the login screen must never reveal
     * whether an email is registered (FT-01, spec 4.1).
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        boolean accountNonLocked = !loginAttemptService.isLocked(email);
        return userRepository.findByEmail(email)
                .map(user -> new MrsUserDetails(user, accountNonLocked))
                .orElseThrow(() -> new UsernameNotFoundException("Email or password is incorrect"));
    }
}
