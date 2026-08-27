package com.funix.swp490x.mrs.domain;

/**
 * Playlist lifecycle state (spec 4.4, DC-01). A Draft is editable by its owner
 * and collaborators; publishing locks it read-only until it is unpublished
 * (FT-06 NAC-06, DC-08).
 */
public enum PlaylistStatus {
    DRAFT,
    PUBLISHED
}
