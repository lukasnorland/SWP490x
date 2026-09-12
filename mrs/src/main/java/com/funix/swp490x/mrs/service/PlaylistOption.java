package com.funix.swp490x.mrs.service;

/**
 * One choice in the Add-to-playlist dialog: an editable Draft (BR-03). Carries
 * the version so the dialog can submit the one it offered (BR-06).
 */
public record PlaylistOption(Long id, String name, long songCount, int version) {
}
