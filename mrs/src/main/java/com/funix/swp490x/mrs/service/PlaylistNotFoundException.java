package com.funix.swp490x.mrs.service;

/** No playlist exists for the requested id, or none the caller may see. */
public class PlaylistNotFoundException extends RuntimeException {

    public PlaylistNotFoundException(Long id) {
        super("No playlist with id " + id);
    }
}
