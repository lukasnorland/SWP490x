package com.funix.swp490x.mrs.service;

import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.RecommendationLogRepository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRS 5.4 / P-06e: RecommendationLog and AuditLog are kept for 12 months and
 * dropped only by this job. There is no manual delete on the screen.
 */
@Component
public class LogRetentionJob {

    static final int RETENTION_MONTHS = 12;

    private static final Logger log = LoggerFactory.getLogger(LogRetentionJob.class);

    private final AuditLogRepository auditLogRepository;
    private final RecommendationLogRepository recommendationLogRepository;

    public LogRetentionJob(AuditLogRepository auditLogRepository,
            RecommendationLogRepository recommendationLogRepository) {
        this.auditLogRepository = auditLogRepository;
        this.recommendationLogRepository = recommendationLogRepository;
    }

    @Scheduled(cron = "0 15 3 * * *")
    public void purge() {
        int removed = purgeOlderThan(LocalDateTime.now().minusMonths(RETENTION_MONTHS));
        if (removed > 0) {
            log.info("Purged {} log row(s) older than {} months", removed, RETENTION_MONTHS);
        }
    }

    /** Package-visible so tests do not wait on the scheduler. */
    @Transactional
    int purgeOlderThan(LocalDateTime cutoff) {
        long audit = auditLogRepository.deleteByTimestampBefore(cutoff);
        long recommendation = recommendationLogRepository.deleteByCreatedAtBefore(cutoff);
        return Math.toIntExact(audit + recommendation);
    }
}
