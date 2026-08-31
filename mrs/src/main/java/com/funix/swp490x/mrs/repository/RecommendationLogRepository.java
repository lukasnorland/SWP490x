package com.funix.swp490x.mrs.repository;

import com.funix.swp490x.mrs.domain.RecommendationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationLogRepository extends JpaRepository<RecommendationLog, Long> {
}
