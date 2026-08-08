package com.funix.swp490x.mrs.web.api.admin;

import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.UserStatus;

/**
 * Partial update for {@code PATCH /api/admin/users/{id}}.
 *
 * <p>Null fields are left unchanged. Status {@code DEACTIVATED} is the soft
 * delete of spec 4.9; {@code ACTIVE} reactivates.
 */
public record UpdateUserRequest(Role role, UserStatus status) {
}
