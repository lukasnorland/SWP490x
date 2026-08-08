package com.funix.swp490x.mrs.web.api;

import com.funix.swp490x.mrs.domain.Role;

/** Signed-in principal summary for {@code GET /api/me}. */
public record MeResponse(
        Long id,
        String email,
        String displayName,
        Role role,
        String roleDisplayName,
        boolean mustChangePassword,
        String landingPath) {
}
