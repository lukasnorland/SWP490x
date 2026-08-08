package com.funix.swp490x.mrs.repository;

import com.funix.swp490x.mrs.domain.Song;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SongRepository extends JpaRepository<Song, Long> {

    /** Update-in-place lookup for the import (DC-04). */
    Optional<Song> findBySourceProviderAndExternalSourceId(String sourceProvider,
            String externalSourceId);

    /**
     * Everything the import diff needs, and nothing else.
     *
     * <p>A sync compares roughly 3,000 external ids against their stored ETags.
     * Hydrating that many entities with their tag collections just to read two
     * columns would dominate the run, so this returns the pairs directly.
     */
    @Query("SELECT s.externalSourceId, s.sourceEtag FROM Song s WHERE s.externalSourceId IS NOT NULL")
    List<Object[]> findExternalIdAndEtagPairs();

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
     * <p>{@code untagged} selects songs carrying no tag at all — the rows DC-03
     * keeps out of filtered search results.
     */
    @Query("""
            SELECT s.id FROM Song s
            WHERE (:provider IS NULL OR s.sourceProvider = :provider)
              AND (:tagId IS NULL OR EXISTS (
                    SELECT 1 FROM Song s2 JOIN s2.tags t2
                    WHERE s2.id = s.id AND t2.id = :tagId))
              AND (:q IS NULL OR LOWER(s.title) LIKE LOWER(CONCAT('%', :q, '%'))
                               OR LOWER(s.artist) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:untagged = FALSE OR s.tags IS EMPTY)
              AND (:noPreview = FALSE OR s.previewUrl IS NULL)
            """)
    Page<Long> searchIds(@Param("provider") String provider,
            @Param("tagId") Long tagId,
            @Param("q") String q,
            @Param("untagged") boolean untagged,
            @Param("noPreview") boolean noPreview,
            Pageable pageable);

    /** The page's songs with their tags, in one query and with no limit. */
    @Query("SELECT s FROM Song s WHERE s.id IN :ids")
    @EntityGraph(attributePaths = "tags")
    List<Song> findAllWithTags(@Param("ids") List<Long> ids);

    /** Provider values actually present, for the P-06b filter. */
    @Query("SELECT DISTINCT s.sourceProvider FROM Song s ORDER BY s.sourceProvider")
    List<String> findDistinctProviders();

    /** Songs no filtered search can reach until they are tagged (DC-03). */
    @Query("SELECT COUNT(s) FROM Song s WHERE s.tags IS EMPTY")
    long countUntagged();
}
