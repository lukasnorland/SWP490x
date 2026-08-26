package com.funix.swp490x.mrs.service;

/** No catalog row exists for the requested id. */
public class SongNotFoundException extends RuntimeException {

    public SongNotFoundException(Long id) {
        super("No song with id " + id);
    }
}
