package com.funix.swp490x.mrs.repository;

import com.funix.swp490x.mrs.domain.RecommendationLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationLogRepository extends JpaRepository<RecommendationLog, Long> {

    @Query("""
            SELECT r FROM RecommendationLog r
            WHERE (:userId IS NULL OR r.userId = :userId)
              AND (:llmSucceeded IS NULL OR r.llmSucceeded = :llmSucceeded)
              AND (:fromTs IS NULL OR r.createdAt >= :fromTs)
              AND (:toTs IS NULL OR r.createdAt < :toTs)
            """)
    Page<RecommendationLog> search(@Param("userId") Long userId,
            @Param("llmSucceeded") Boolean llmSucceeded,
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toTs") LocalDateTime toTs,
            Pageable pageable);

    @Query("SELECT DISTINCT r.userId FROM RecommendationLog r ORDER BY r.userId")
    List<Long> findDistinctUserIds();

    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
