package com.funix.swp490x.mrs.repository;

import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, Long> {

    /** Unique per {@code (type, name)}; the import's get-or-create lookup. */
    Optional<Tag> findByTypeAndName(TagType type, String name);

    List<Tag> findByTypeOrderByName(TagType type);

    /** Interpretation accepts existing dictionary entries even before their first song. */
    List<Tag> findAllByOrderByTypeAscNameAsc();

    /**
     * How many songs currently carry each dictionary row. Tags with no songs
     * still appear (count 0) so P-06b can retire unused names.
     */
    @Query("SELECT t.id, COUNT(s.id) FROM Song s JOIN s.tags t GROUP BY t.id")
    List<Object[]> countSongsGroupedByTagId();

    /**
     * Songs currently carrying this dictionary row. Delete is refused above
     * zero — stripping {@code song_tag} would desync staged JSON.
     */
    @Query("SELECT COUNT(s.id) FROM Song s JOIN s.tags t WHERE t.id = :id")
    long countSongsWithTag(@Param("id") Long id);

    /**
     * Names currently on at least one song — the catalog filter dropdowns.
     * Orphan dictionary rows (a genre moved off every song) stay out.
     */
    @Query("""
            SELECT t FROM Tag t
            WHERE EXISTS (
                SELECT 1 FROM Song s JOIN s.tags attached WHERE attached = t)
            ORDER BY t.type ASC, t.name ASC
            """)
    List<Tag> findAllUsedOrderByTypeAscNameAsc();

    /**
     * Drops dictionary rows no song still carries. Sync detaches names that
     * left the staged JSON but does not delete the {@code tag} row otherwise.
     */
    @Modifying
    @Query(value = """
            DELETE FROM tag
            WHERE NOT EXISTS (
                SELECT 1 FROM song_tag WHERE song_tag.tag_id = tag.id)
            """, nativeQuery = true)
    int deleteUnused();
}
