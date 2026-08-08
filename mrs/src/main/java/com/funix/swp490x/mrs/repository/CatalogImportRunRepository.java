package com.funix.swp490x.mrs.repository;

import com.funix.swp490x.mrs.domain.CatalogImportRun;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogImportRunRepository extends JpaRepository<CatalogImportRun, Long> {

    /** The "last run" panel on P-06c. */
    Optional<CatalogImportRun> findFirstByOrderByStartedAtDesc();
}
