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

    /**
     * Position to park a row at while its neighbours are renumbered.
     *
     * <p>{@code uq_playlistsong_position} is checked per row as a statement
     * runs, so writing 3 -> 2 while some other row still holds 2 fails even
     * though the finished state would be valid. Every renumbering therefore
     * moves rows out to this disjoint range first and brings them back second.
     * It stays positive, so {@code ck_plsong_position} holds throughout.
     */
    int PARK_OFFSET = 1_000_000;

    /** One playlist's songs in order, with the tags the detail table shows. */
    @Query("SELECT ps FROM PlaylistSong ps WHERE ps.id.playlistId = :playlistId "
            + "ORDER BY ps.position ASC")
    @EntityGraph(attributePaths = {"song", "song.tags"})
    List<PlaylistSong> findOrdered(@Param("playlistId") Long playlistId);

    Optional<PlaylistSong> findByIdPlaylistIdAndIdSongId(Long playlistId, Long songId);

    /**
     * Playlist membership of one catalog song, before a detach, so the
     * remaining rows can be compacted to 1..N (DC-04).
     */
    @Query("SELECT ps.id.playlistId AS playlistId, ps.position AS position "
            + "FROM PlaylistSong ps WHERE ps.id.songId = :songId")
    List<PlaylistSlot> findSlotsBySongId(@Param("songId") Long songId);

    /** Same projection for a prune that drops several songs at once. */
    @Query("SELECT ps.id.playlistId AS playlistId, ps.position AS position "
            + "FROM PlaylistSong ps WHERE ps.id.songId IN :songIds")
    List<PlaylistSlot> findSlotsBySongIdIn(@Param("songIds") Collection<Long> songIds);

    /**
     * Positions only, in order. Used after parking so the compact can write
     * 1..N without hydrating stale {@link PlaylistSong} entities.
     */
    @Query("SELECT ps.position FROM PlaylistSong ps WHERE ps.id.playlistId = :playlistId "
            + "ORDER BY ps.position ASC")
    List<Integer> findPositionsOrdered(@Param("playlistId") Long playlistId);

    boolean existsByIdPlaylistIdAndIdSongId(Long playlistId, Long songId);

    /** One song at one position in one playlist. Aliases match the getters. */
    interface PlaylistSlot {
        Long getPlaylistId();

        int getPosition();
    }

    long countByIdPlaylistId(Long playlistId);

    @Query("SELECT COALESCE(MAX(ps.position), 0) FROM PlaylistSong ps "
            + "WHERE ps.id.playlistId = :playlistId")
    int findMaxPosition(@Param("playlistId") Long playlistId);

    @Query("SELECT COALESCE(SUM(ps.song.duration), 0L) FROM PlaylistSong ps "
            + "WHERE ps.id.playlistId = :playlistId")
    long totalDuration(@Param("playlistId") Long playlistId);

    /**
     * Shifts every row past {@code after} by {@code delta}. See
     * {@link #PARK_OFFSET}.
     *
     * <p>{@code flushAutomatically} on this and the writes below because they
     * run as a sequence whose intermediate states matter: a pending insert
     * still sitting in the persistence context would land after the
     * renumbering and take a position that is no longer free.
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

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PlaylistSong ps WHERE ps.id.playlistId = :playlistId "
            + "AND ps.id.songId = :songId")
    int deleteSong(@Param("playlistId") Long playlistId, @Param("songId") Long songId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PlaylistSong ps WHERE ps.id.playlistId = :playlistId")
    int deleteAllOf(@Param("playlistId") Long playlistId);
}
