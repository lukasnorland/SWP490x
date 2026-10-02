package com.funix.swp490x.mrs.service;

import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import java.time.LocalDateTime;

/** Account view record without a password hash or lazy entity associations (TDS 2.5). */
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
