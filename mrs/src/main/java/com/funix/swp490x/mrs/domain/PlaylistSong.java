package com.funix.swp490x.mrs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One song at one position in one playlist. Maps the {@code playlist_song}
 * table created by Flyway V1.
 *
 * <p>An entity rather than a {@code @ManyToMany} with {@code @OrderColumn}:
 * order columns are zero-based, which the {@code ck_plsong_position} check
 * forbids, and {@code uq_playlistsong_position} means every renumbering has to
 * be sequenced explicitly anyway (see {@code PlaylistService}).
 */
@Entity
@Table(name = "playlist_song")
public class PlaylistSong {

    @EmbeddedId
    private PlaylistSongId id;

    /**
     * The catalog row, so the detail table can render a song without a second
     * lookup. Read-only, because {@code song_id} is already written by the
     * composite key.
     */
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
