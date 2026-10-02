package com.funix.swp490x.mrs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Playlist membership with a positive 1-based position (DC-04).
 * Explicit renumbering preserves the unique-position constraint.
 */
@Entity
@Table(name = "playlist_song")
public class PlaylistSong {

    @EmbeddedId
    private PlaylistSongId id;

    /** Read-only song association; the composite key owns {@code song_id}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false, insertable = false, updatable = false)
    private Song song;

    /** Contiguous 1..N per playlist (DC-04). */
    @Column(nullable = false)
    private int position;

    protected PlaylistSong() {
    }

    public PlaylistSong(Long playlistId, Song song, int position) {
        this.id = new PlaylistSongId(playlistId, song.getId());
        this.song = song;
        this.position = position;
    }

    public PlaylistSongId getId() {
        return id;
    }

    public Song getSong() {
        return song;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
