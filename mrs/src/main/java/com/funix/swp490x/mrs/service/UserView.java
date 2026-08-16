package com.funix.swp490x.mrs.service;

import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import java.time.LocalDateTime;

/**
 * Read model for an account. Services return this rather than the {@link User}
 * entity so a template or mailer cannot touch a lazy association or a password
 * hash (TDS Part 2.5).
 */
public record UserView(
        Long id,
        String username,
        String email,
        Role role,
        UserStatus status,
        LocalDateTime createdAt,
        boolean mustChangePassword) {

    public static UserView of(User user) {
        return new UserView(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.isMustChangePassword());
    }
}
