package com.funix.swp490x.mrs.repository;

import com.funix.swp490x.mrs.domain.PlaylistSong;
import com.funix.swp490x.mrs.domain.PlaylistSongId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistSongRepository extends JpaRepository<PlaylistSong, PlaylistSongId> {

    /** Positive temporary offset used before renumbering to avoid unique-position collisions. */
    int PARK_OFFSET = 1_000_000;

    /** One playlist's songs in order, with the tags the detail table shows. */
    @Query("SELECT ps FROM PlaylistSong ps WHERE ps.id.playlistId = :playlistId "
            + "ORDER BY ps.position ASC")
    @EntityGraph(attributePaths = {"song", "song.tags"})
    List<PlaylistSong> findOrdered(@Param("playlistId") Long playlistId);

    Optional<PlaylistSong> findByIdPlaylistIdAndIdSongId(Long playlistId, Long songId);

    /** Finds affected playlists before song removal so positions can be compacted (DC-04). */
    @Query("SELECT DISTINCT ps.id.playlistId FROM PlaylistSong ps WHERE ps.id.songId = :songId")
    List<Long> findPlaylistIdsBySongId(@Param("songId") Long songId);

    /** Same lookup for a prune that drops several songs at once. */
    @Query("SELECT DISTINCT ps.id.playlistId FROM PlaylistSong ps WHERE ps.id.songId IN :songIds")
    List<Long> findPlaylistIdsBySongIdIn(@Param("songIds") Collection<Long> songIds);

    boolean existsByIdPlaylistIdAndIdSongId(Long playlistId, Long songId);

    long countByIdPlaylistId(Long playlistId);

    @Query("SELECT COALESCE(MAX(ps.position), 0) FROM PlaylistSong ps "
            + "WHERE ps.id.playlistId = :playlistId")
    int findMaxPosition(@Param("playlistId") Long playlistId);

    @Query("SELECT COALESCE(SUM(ps.song.duration), 0L) FROM PlaylistSong ps "
            + "WHERE ps.id.playlistId = :playlistId")
    long totalDuration(@Param("playlistId") Long playlistId);

    /**
     * Shifts positions after {@code after}; see {@link #PARK_OFFSET}.
     * Flush pending inserts before renumbering to preserve unique positions.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE PlaylistSong ps SET ps.position = ps.position + :delta "
            + "WHERE ps.id.playlistId = :playlistId AND ps.position > :after")
    int shiftAfter(@Param("playlistId") Long playlistId,
            @Param("after") int after,
            @Param("delta") int delta);

    /** Moves the single row currently at {@code from} to {@code to}. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE PlaylistSong ps SET ps.position = :to "
            + "WHERE ps.id.playlistId = :playlistId AND ps.position = :from")
    int moveOne(@Param("playlistId") Long playlistId,
            @Param("from") int from,
            @Param("to") int to);

    /**
     * Writes contiguous 1..N in current order after parking rows.
     * Uses a native window-function query for bulk renumbering.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE playlist_song ps
            INNER JOIN (
                SELECT song_id, ROW_NUMBER() OVER (ORDER BY position) AS n
                FROM playlist_song
                WHERE playlist_id = :playlistId
            ) ranked ON ranked.song_id = ps.song_id
            SET ps.position = ranked.n
            WHERE ps.playlist_id = :playlistId
            """, nativeQuery = true)
    int assignDensePositions(@Param("playlistId") Long playlistId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PlaylistSong ps WHERE ps.id.playlistId = :playlistId "
            + "AND ps.id.songId = :songId")
    int deleteSong(@Param("playlistId") Long playlistId, @Param("songId") Long songId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PlaylistSong ps WHERE ps.id.playlistId = :playlistId")
    int deleteAllOf(@Param("playlistId") Long playlistId);
}
