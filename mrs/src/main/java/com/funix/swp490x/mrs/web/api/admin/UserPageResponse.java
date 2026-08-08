package com.funix.swp490x.mrs.web.api.admin;

import java.util.List;

/** Paged list envelope for {@code GET /api/admin/users}. */
public record UserPageResponse(
        List<UserResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {
}
