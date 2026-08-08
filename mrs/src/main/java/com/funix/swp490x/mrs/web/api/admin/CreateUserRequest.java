package com.funix.swp490x.mrs.web.api.admin;

import com.funix.swp490x.mrs.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for {@code POST /api/admin/users} (UC-07). */
public record CreateUserRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 255) String email,
        @NotNull Role role,
        @NotBlank String password) {
}
