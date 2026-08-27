package com.funix.swp490x.mrs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** Composite key of {@code playlist_song}: one song appears at most once. */
@Embeddable
public class PlaylistSongId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "playlist_id", nullable = false)
    private Long playlistId;

    @Column(name = "song_id", nullable = false)
    private Long songId;

    protected PlaylistSongId() {
    }

    public PlaylistSongId(Long playlistId, Long songId) {
        this.playlistId = playlistId;
        this.songId = songId;
    }

    public Long getPlaylistId() {
        return playlistId;
    }

    public Long getSongId() {
        return songId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlaylistSongId that)) {
            return false;
        }
        return Objects.equals(playlistId, that.playlistId) && Objects.equals(songId, that.songId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playlistId, songId);
    }
}
