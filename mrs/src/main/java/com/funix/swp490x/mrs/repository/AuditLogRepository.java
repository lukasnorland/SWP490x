package com.funix.swp490x.mrs.repository;

import com.funix.swp490x.mrs.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
