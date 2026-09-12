package com.funix.swp490x.mrs.repository;

import com.funix.swp490x.mrs.domain.AuditLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:actorId IS NULL OR a.actorId = :actorId)
              AND (:action IS NULL OR a.action = :action)
              AND (:fromTs IS NULL OR a.timestamp >= :fromTs)
              AND (:toTs IS NULL OR a.timestamp < :toTs)
            """)
    Page<AuditLog> search(@Param("actorId") Long actorId,
            @Param("action") String action,
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toTs") LocalDateTime toTs,
            Pageable pageable);

    @Query("SELECT DISTINCT a.actorId FROM AuditLog a ORDER BY a.actorId")
    List<Long> findDistinctActorIds();

    @Query("SELECT DISTINCT a.action FROM AuditLog a ORDER BY a.action")
    List<String> findDistinctActions();

    long deleteByTimestampBefore(LocalDateTime cutoff);
}
