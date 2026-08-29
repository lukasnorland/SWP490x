package com.funix.swp490x.mrs.repository;

import com.funix.swp490x.mrs.domain.Song;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SongRepository extends JpaRepository<Song, Long> {

    /** Update-in-place lookup for the import (DC-04). */
    Optional<Song> findBySourceProviderAndExternalSourceId(String sourceProvider,
            String externalSourceId);

    Optional<Song> findByIsrc(String isrc);

    /**
     * Everything the import diff needs, and nothing else.
     *
     * <p>A sync compares roughly 3,000 external ids against their stored ETags.
     * Hydrating that many entities with their tag collections just to read two
     * columns would dominate the run, so this returns the pairs directly.
     */
    @Query("SELECT s.externalSourceId, s.sourceEtag FROM Song s WHERE s.externalSourceId IS NOT NULL")
    List<Object[]> findExternalIdAndEtagPairs();

    /** Ids plus staging filenames, so a sync can drop rows whose object left the prefix. */
    @Query("SELECT s.id, s.externalSourceId FROM Song s WHERE s.externalSourceId IS NOT NULL")
    List<Object[]> findIdAndExternalIdPairs();

    @Query("SELECT s.id FROM Song s WHERE s.externalSourceId IN :extIds")
    List<Long> findIdsByExternalSourceIdIn(@Param("extIds") Collection<String> extIds);

    /** playlist_song has no ON DELETE CASCADE, so detach before removing the song. */
    @Modifying
    @Query(value = "DELETE FROM playlist_song WHERE song_id IN :ids", nativeQuery = true)
    void detachFromPlaylists(@Param("ids") Collection<Long> ids);

    @Modifying
    @Query("DELETE FROM Song s WHERE s.externalSourceId IN :extIds")
    int deleteByExternalSourceIdIn(@Param("extIds") Collection<String> extIds);

    /**
     * P-06b Zone B/C — ids of the songs on one page, filtered.
     *
     * <p>Ids rather than entities, and the tag filter is an EXISTS rather than a
     * join, for two reasons: a join would need DISTINCT, which MySQL refuses to
     * combine with an ORDER BY on a column outside the select list; and paging a
     * query that also fetches a collection makes Hibernate apply the limit in
     * memory, which over 3,000 songs means loading the whole catalog per page.
     * {@link #findAllWithTags} then fetches just this page's tags.
     *
     * <p>Genre, mood and freeform tag filters are OR within a vocabulary and
     * AND across them: a song must match at least one selected value in each
     * category that has a selection. An empty list leaves that vocabulary
     * unconstrained. Hibernate rejects {@code IN ()}, so an unused list is
     * bound to a dummy value and skipped with a boolean flag.
     */
    @Query("""
            SELECT s.id FROM Song s
            WHERE (:providerEmpty = true OR s.sourceProvider IN :providers)
              AND (:genreEmpty = true OR EXISTS (
                    SELECT 1 FROM Song sg JOIN sg.tags tg
                    WHERE sg.id = s.id AND tg.id IN :genreIds))
              AND (:moodEmpty = true OR EXISTS (
                    SELECT 1 FROM Song sm JOIN sm.tags tm
                    WHERE sm.id = s.id AND tm.id IN :moodIds))
              AND (:tagEmpty = true OR EXISTS (
                    SELECT 1 FROM Song st JOIN st.tags tt
                    WHERE st.id = s.id AND tt.id IN :tagIds))
              AND (:q IS NULL OR LOWER(s.title) LIKE LOWER(CONCAT('%', :q, '%'))
                               OR LOWER(s.artist) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Long> searchIds(@Param("providerEmpty") boolean providerEmpty,
            @Param("providers") Collection<String> providers,
            @Param("genreEmpty") boolean genreEmpty,
            @Param("genreIds") Collection<Long> genreIds,
            @Param("moodEmpty") boolean moodEmpty,
            @Param("moodIds") Collection<Long> moodIds,
            @Param("tagEmpty") boolean tagEmpty,
            @Param("tagIds") Collection<Long> tagIds,
            @Param("q") String q,
            Pageable pageable);

    /** The page's songs with their tags, in one query and with no limit. */
    @Query("SELECT s FROM Song s WHERE s.id IN :ids")
    @EntityGraph(attributePaths = "tags")
    List<Song> findAllWithTags(@Param("ids") List<Long> ids);

    /** One song with its tags, for the P-06b edit modal (UC-29). */
    @Query("SELECT s FROM Song s WHERE s.id = :id")
    @EntityGraph(attributePaths = "tags")
    Optional<Song> findByIdWithTags(@Param("id") Long id);

    /** Provider values actually present, for the P-06b filter. */
    @Query("SELECT DISTINCT s.sourceProvider FROM Song s ORDER BY s.sourceProvider")
    List<String> findDistinctProviders();

    /** Songs no filtered search can reach until they are tagged (DC-03). */
    @Query("SELECT COUNT(s) FROM Song s WHERE s.tags IS EMPTY")
    long countUntagged();

    /**
     * Songs whose cover has never been sampled for the shell's wash, or whose
     * cover has changed since it was.
     *
     * <p>Id and url rather than entities: an import may look at thousands of
     * these, and hydrating a song with its tags to read one column would cost
     * far more than the update that follows.
     */
    @Query("""
            SELECT s.id, s.coverUrl FROM Song s
            WHERE s.coverUrl IS NOT NULL
              AND (s.ambienceSourceUrl IS NULL OR s.ambienceSourceUrl <> s.coverUrl)
            ORDER BY s.id
            """)
    List<Object[]> findCoversNeedingAmbience(Pageable pageable);

    /**
     * Records what a cover gave, deliberately without touching {@code version}:
     * wash colours are derived, so recomputing them must not collide with a
     * designer's own edit (BR-06).
     */
    @Modifying
    @Query("""
            UPDATE Song s
            SET s.ambienceA = :a, s.ambienceB = :b, s.ambienceSourceUrl = :sourceUrl
            WHERE s.id = :id
            """)
    void recordAmbience(@Param("id") Long id,
            @Param("a") String a,
            @Param("b") String b,
            @Param("sourceUrl") String sourceUrl);
}
