package com.funix.swp490x.mrs.service;

/**
 * Carries the submitted/current versions for a rejected playlist update (UC-19, BR-06).
 * Content edits may be cloned into a new Draft (BR-11).
 */
public class StalePlaylistException extends RuntimeException {

    private final transient Long playlistId;
    private final int expectedVersion;
    private final int currentVersion;

    public StalePlaylistException(Long playlistId, int expectedVersion, int currentVersion) {
        super("Playlist " + playlistId + " changed while it was being edited");
        this.playlistId = playlistId;
        this.expectedVersion = expectedVersion;
        this.currentVersion = currentVersion;
    }

    public Long getPlaylistId() {
        return playlistId;
    }

    public int getExpectedVersion() {
        return expectedVersion;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }
}
