package com.funix.swp490x.mrs.repository;

import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

    /** Unique per {@code (type, name)}; the import's get-or-create lookup. */
    Optional<Tag> findByTypeAndName(TagType type, String name);

    List<Tag> findByTypeOrderByName(TagType type);

    List<Tag> findAllByOrderByTypeAscNameAsc();
}
