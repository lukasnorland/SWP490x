package com.funix.swp490x.mrs.service;

/**
 * ADMIN must pick a successor for every playlist the leaving Designer owns
 * before deactivation or demotion can proceed.
 */
public class PlaylistSuccessorRequiredException extends RuntimeException {

    public PlaylistSuccessorRequiredException() {
        super("Choose who should own each playlist before continuing.");
    }
}
