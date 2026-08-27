package com.funix.swp490x.mrs.service;

/**
 * A Published playlist is read-only until it is unpublished
 * (FT-06 NAC-06, DC-08).
 */
public class PlaylistLockedException extends RuntimeException {

    public PlaylistLockedException(Long id) {
        super("Playlist " + id + " is published and cannot be edited");
    }
}
