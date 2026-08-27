package com.funix.swp490x.mrs.service;

/**
 * The playlist cannot make the requested transition: publishing with no songs
 * (BR-05), or deleting one that is still Published (DC-05).
 */
public class InvalidPlaylistStateException extends RuntimeException {

    public InvalidPlaylistStateException(String message) {
        super(message);
    }
}
