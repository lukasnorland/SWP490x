package com.funix.swp490x.mrs.service;

/**
 * UC-19 / BR-06: the submitted version is not the one currently stored, so the
 * change must not overwrite whoever saved first. Unlike a song, a playlist
 * conflict also offers a clone (BR-11), so the stored version travels with the
 * exception for the conflict screen to report (NAC-02).
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
