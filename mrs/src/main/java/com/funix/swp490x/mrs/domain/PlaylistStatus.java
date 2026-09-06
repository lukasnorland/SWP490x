package com.funix.swp490x.mrs.domain;

/**
 * Playlist lifecycle state (spec 4.4, DC-01). A Draft is editable by its owner
 * and collaborators; the owner or an administrator can publish or unpublish.
 * Only the owner can delete. Publishing locks song edits until unpublished
 * (FT-06 NAC-06, DC-08).
 */
public enum PlaylistStatus {
    DRAFT,
    PUBLISHED
}
