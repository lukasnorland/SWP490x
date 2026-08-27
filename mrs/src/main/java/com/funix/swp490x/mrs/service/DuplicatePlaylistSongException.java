package com.funix.swp490x.mrs.service;

/** The song is already in the playlist; {@code playlist_song} is keyed by both. */
public class DuplicatePlaylistSongException extends RuntimeException {

    public DuplicatePlaylistSongException(Long playlistId, Long songId) {
        super("Song " + songId + " is already in playlist " + playlistId);
    }
}
