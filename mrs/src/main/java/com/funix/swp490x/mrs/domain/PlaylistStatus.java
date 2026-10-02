package com.funix.swp490x.mrs.domain;

/**
 * Draft content is editable by owner/collaborators; Published content is locked (DC-08).
 * Owner or ADMIN may publish/unpublish; only the owner may delete a Draft (BR-03, BR-16).
 */
public enum PlaylistStatus {
    DRAFT,
    PUBLISHED
}
