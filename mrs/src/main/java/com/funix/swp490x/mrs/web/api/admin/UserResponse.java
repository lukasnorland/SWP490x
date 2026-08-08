package com.funix.swp490x.mrs.web.api.admin;

import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import java.time.LocalDateTime;

/** Account resource returned by {@code /api/admin/users}. */
public record UserResponse(
        Long id,
        String name,
        String email,
        Role role,
        String roleDisplayName,
        UserStatus status,
        boolean mustChangePassword,
        LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getRole().getDisplayName(),
                user.getStatus(),
                user.isMustChangePassword(),
                user.getCreatedAt());
    }
}
