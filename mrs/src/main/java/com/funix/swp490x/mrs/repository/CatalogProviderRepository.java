package com.funix.swp490x.mrs.repository;

import com.funix.swp490x.mrs.domain.CatalogProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogProviderRepository extends JpaRepository<CatalogProvider, Long> {

    List<CatalogProvider> findAllByOrderByNameAsc();

    Optional<CatalogProvider> findByNameIgnoreCase(String name);

    Optional<CatalogProvider> findBySlugIgnoreCase(String slug);

    boolean existsByNameIgnoreCase(String name);

    boolean existsBySlugIgnoreCase(String slug);
}
