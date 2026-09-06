package com.funix.swp490x.mrs.domain;

/**
 * Playlist lifecycle state (spec 4.4, DC-01). A Draft is editable by its owner
 * and collaborators; only the owner can publish, unpublish, or delete.
 * Publishing locks song edits until the owner unpublishes it (FT-06 NAC-06, DC-08).
 */
public enum PlaylistStatus {
    DRAFT,
    PUBLISHED
}
