package com.funix.swp490x.mrs.service;

import com.funix.swp490x.mrs.domain.PlaylistStatus;
import java.time.LocalDateTime;

/**
 * One row of the P-03a table. A read model rather than the entity, so the
 * template cannot reach a lazy association with the view already rendering
 * ({@code spring.jpa.open-in-view=false}).
 */
public record PlaylistSummary(
        Long id,
        String name,
        PlaylistStatus status,
        long songCount,
        int version,
        LocalDateTime lastModifiedAt,
        String lastModifiedByName,
        long collaboratorCount,
        boolean sharedWithMe,
        String ownerName) {

    /** Draft is the only editable state (DC-08); delete is Draft-only too (DC-05). */
    public boolean isDraft() {
        return status == PlaylistStatus.DRAFT;
    }
}
