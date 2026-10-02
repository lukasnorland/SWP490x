package com.funix.swp490x.mrs.repository;

import com.funix.swp490x.mrs.domain.Playlist;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    /** Every playlist an account owns, Draft or Published. */
    List<Playlist> findByOwnerId(Long ownerId);

    /** True when any playlist already uses this name (uq_playlist_name). */
    boolean existsByName(String name);

    /** Playlists that would lose a song if this catalog provider were removed. */
    @Query("""
            SELECT COUNT(DISTINCT ps.id.playlistId) FROM PlaylistSong ps
            WHERE LOWER(ps.song.sourceProvider) = LOWER(:provider)
            """)
    long countPlaylistsContainingProvider(@Param("provider") String provider);

    /** True when a different playlist already uses this name. */
    boolean existsByNameAndIdNot(String name, Long id);

    /** Removes grants when an account leaves the Content Designer role (BR-03). */
    @Modifying
    @Query(value = "DELETE FROM playlist_collaborator WHERE user_id = :userId", nativeQuery = true)
    int deleteCollaboratorGrantsOf(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            DELETE FROM playlist_collaborator
            WHERE playlist_id = :playlistId AND user_id = :userId
            """, nativeQuery = true)
    int deleteCollaboratorGrant(@Param("playlistId") Long playlistId, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO playlist_collaborator (playlist_id, user_id, granted_by, granted_at)
            VALUES (:playlistId, :userId, :grantedBy, CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    int insertCollaboratorGrant(@Param("playlistId") Long playlistId,
            @Param("userId") Long userId,
            @Param("grantedBy") Long grantedBy);

    @Query(value = """
            SELECT u.id AS id, u.username AS name
            FROM playlist_collaborator c
            JOIN users u ON u.id = c.user_id
            WHERE c.playlist_id = :playlistId
            ORDER BY c.granted_at ASC, u.username ASC
            """, nativeQuery = true)
    List<OwnerOption> findCollaborators(@Param("playlistId") Long playlistId);

    @Query(value = """
            SELECT COUNT(*) FROM playlist_collaborator
            WHERE playlist_id = :playlistId AND user_id = :userId
            """, nativeQuery = true)
    long countCollaboratorGrant(@Param("playlistId") Long playlistId, @Param("userId") Long userId);

    /**
     * Pages playlists owned by or shared with the user (BR-03).
     * Empty strings represent unset text/status filters in native queries.
     */
    @Query(value = """
            SELECT p.id FROM playlist p
            WHERE (p.owner_id = :userId
                   OR EXISTS (SELECT 1 FROM playlist_collaborator c
                              WHERE c.playlist_id = p.id AND c.user_id = :userId))
              AND (:status = '' OR p.status = :status)
              AND (:q = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY p.last_modified_at DESC, p.id DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM playlist p
            WHERE (p.owner_id = :userId
                   OR EXISTS (SELECT 1 FROM playlist_collaborator c
                              WHERE c.playlist_id = p.id AND c.user_id = :userId))
              AND (:status = '' OR p.status = :status)
              AND (:q = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')))
            """,
            nativeQuery = true)
    Page<Long> searchVisibleIds(@Param("userId") Long userId,
            @Param("status") String status,
            @Param("q") String q,
            Pageable pageable);

    /**
     * Ids of one page of every playlist in the system, for ADMIN oversight.
     * {@code ownerId} is 0 for "any owner"; {@code status} and {@code q} are
     * empty strings when unset, as in {@link #searchVisibleIds}.
     */
    @Query(value = """
            SELECT p.id FROM playlist p
            WHERE (:ownerId = 0 OR p.owner_id = :ownerId)
              AND (:status = '' OR p.status = :status)
              AND (:q = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY p.last_modified_at DESC, p.id DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM playlist p
            WHERE (:ownerId = 0 OR p.owner_id = :ownerId)
              AND (:status = '' OR p.status = :status)
              AND (:q = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')))
            """,
            nativeQuery = true)
    Page<Long> searchAllIds(@Param("ownerId") long ownerId,
            @Param("status") String status,
            @Param("q") String q,
            Pageable pageable);

    /** Everyone who owns at least one playlist, Draft or Published. */
    @Query(value = """
            SELECT DISTINCT u.id AS id, u.username AS name
            FROM playlist p
            JOIN users u ON u.id = p.owner_id
            ORDER BY u.username
            """, nativeQuery = true)
    List<OwnerOption> findPlaylistOwners();

    /** Projects playlist rows with song/collaborator counts; preserves empty playlists. */
    @Query(value = """
            SELECT p.id               AS id,
                   p.name             AS name,
                   p.status           AS status,
                   p.version          AS version,
                   p.last_modified_at AS lastModifiedAt,
                   u.username         AS lastModifiedByName,
                   (SELECT COUNT(*) FROM playlist_song ps
                    WHERE ps.playlist_id = p.id)         AS songCount,
                   (SELECT COUNT(*) FROM playlist_collaborator c
                    WHERE c.playlist_id = p.id)          AS collaboratorCount,
                   p.owner_id                            AS ownerId,
                   o.username                            AS ownerName
            FROM playlist p
            JOIN users u ON u.id = p.last_modified_by
            JOIN users o ON o.id = p.owner_id
            WHERE p.id IN (:ids)
            """, nativeQuery = true)
    List<SummaryRow> findSummaries(@Param("ids") Collection<Long> ids);

    /** Drafts the user may add a song to, for the Add-to-playlist dialog. */
    @Query(value = """
            SELECT p.id FROM playlist p
            WHERE p.status = 'DRAFT'
              AND (p.owner_id = :userId
                   OR EXISTS (SELECT 1 FROM playlist_collaborator c
                              WHERE c.playlist_id = p.id AND c.user_id = :userId))
            ORDER BY p.last_modified_at DESC, p.id DESC
            """, nativeQuery = true)
    List<Long> findEditableDraftIds(@Param("userId") Long userId);

    /** Returns a count for owner/collaborator visibility; avoids native numeric-to-boolean conversion. */
    @Query(value = """
            SELECT COUNT(*) FROM playlist p
            WHERE p.id = :playlistId
              AND (p.owner_id = :userId
                   OR EXISTS (SELECT 1 FROM playlist_collaborator c
                              WHERE c.playlist_id = p.id AND c.user_id = :userId))
            """, nativeQuery = true)
    long countVisibleTo(@Param("playlistId") Long playlistId, @Param("userId") Long userId);

    /**
     * Pages all Published playlists, visible to every internal user (BR-04).
     * {@code ownerId} is 0 when no owner filter is selected.
     */
    @Query(value = """
            SELECT p.id FROM playlist p
            WHERE p.status = 'PUBLISHED'
              AND (:ownerId = 0 OR p.owner_id = :ownerId)
              AND (:q = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY p.published_at DESC, p.id DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM playlist p
            WHERE p.status = 'PUBLISHED'
              AND (:ownerId = 0 OR p.owner_id = :ownerId)
              AND (:q = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')))
            """,
            nativeQuery = true)
    Page<Long> searchPublishedIds(@Param("ownerId") long ownerId,
            @Param("q") String q,
            Pageable pageable);

    @Query(value = """
            SELECT p.id            AS id,
                   p.name          AS name,
                   p.owner_id      AS ownerId,
                   u.username      AS ownerName,
                   p.published_at  AS publishedAt,
                   (SELECT COUNT(*) FROM playlist_song ps
                    WHERE ps.playlist_id = p.id) AS songCount
            FROM playlist p
            JOIN users u ON u.id = p.owner_id
            WHERE p.id IN (:ids)
            """, nativeQuery = true)
    List<PublishedCardRow> findPublishedCards(@Param("ids") Collection<Long> ids);

    /** First four covers, in playing order, for the card mosaic. */
    @Query(value = """
            SELECT ps.playlist_id AS playlistId,
                   s.cover_url    AS coverUrl,
                   ps.position    AS position
            FROM playlist_song ps
            JOIN song s ON s.id = ps.song_id
            WHERE ps.playlist_id IN (:ids) AND ps.position <= 4
            ORDER BY ps.playlist_id, ps.position
            """, nativeQuery = true)
    List<CoverTile> findCoverTiles(@Param("ids") Collection<Long> ids);

    /** Tag frequencies per playlist, so the card can show the top three. */
    @Query(value = """
            SELECT ps.playlist_id AS playlistId,
                   t.name         AS name,
                   COUNT(*)       AS uses
            FROM playlist_song ps
            JOIN song_tag st ON st.song_id = ps.song_id
            JOIN tag t ON t.id = st.tag_id
            WHERE ps.playlist_id IN (:ids)
            GROUP BY ps.playlist_id, t.name
            """, nativeQuery = true)
    List<TagCount> findTagCounts(@Param("ids") Collection<Long> ids);

    @Query(value = """
            SELECT DISTINCT u.id AS id, u.username AS name
            FROM playlist p
            JOIN users u ON u.id = p.owner_id
            WHERE p.status = 'PUBLISHED'
            ORDER BY u.username
            """, nativeQuery = true)
    List<OwnerOption> findPublishedOwners();

    /** One page of the P-03a table, projected by column alias. */
    interface SummaryRow {
        Long getId();

        String getName();

        String getStatus();

        Integer getVersion();

        LocalDateTime getLastModifiedAt();

        String getLastModifiedByName();

        Long getSongCount();

        Long getCollaboratorCount();

        String getOwnerName();

        /** Returns the owner id so the service can derive {@code sharedWithMe}. */
        Long getOwnerId();
    }

    interface PublishedCardRow {
        Long getId();

        String getName();

        Long getOwnerId();

        String getOwnerName();

        LocalDateTime getPublishedAt();

        Long getSongCount();
    }

    interface CoverTile {
        Long getPlaylistId();

        String getCoverUrl();

        Integer getPosition();
    }

    interface TagCount {
        Long getPlaylistId();

        String getName();

        Long getUses();
    }

    interface OwnerOption {
        Long getId();

        String getName();
    }
}
