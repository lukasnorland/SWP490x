package com.funix.swp490x.mrs.service;

/**
 * UC-29 / BR-06: the submitted version is not the one currently stored, so
 * the edit must not overwrite the other change. Songs have no clone option.
 */
public class StaleSongException extends RuntimeException {

    public StaleSongException(Long id) {
        super("Song " + id + " changed while it was being edited");
    }
}
