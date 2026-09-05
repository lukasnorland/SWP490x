package com.funix.swp490x.mrs.service;

import com.funix.swp490x.mrs.domain.PlaylistStatus;
import java.util.List;

/**
 * One owned playlist on the ADMIN successor confirm page, with the
 * collaborators that may take ownership.
 */
public record PlaylistSuccessorChoice(
        Long playlistId,
        String name,
        PlaylistStatus status,
        List<PlaylistOwner> collaborators) {
}
