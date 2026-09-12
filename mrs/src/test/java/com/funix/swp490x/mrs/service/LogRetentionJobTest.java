package com.funix.swp490x.mrs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.RecommendationLogRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogRetentionJobTest {

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private RecommendationLogRepository recommendationLogRepository;

    private LogRetentionJob job;

    @BeforeEach
    void setUp() {
        job = new LogRetentionJob(auditLogRepository, recommendationLogRepository);
    }

    @Test
    void purgeOlderThanDeletesBothTablesAndSumsTheRows() {
        LocalDateTime cutoff = LocalDateTime.of(2025, 9, 12, 3, 15);
        given(auditLogRepository.deleteByTimestampBefore(cutoff)).willReturn(2L);
        given(recommendationLogRepository.deleteByCreatedAtBefore(cutoff)).willReturn(3L);

        assertThat(job.purgeOlderThan(cutoff)).isEqualTo(5);

        then(auditLogRepository).should().deleteByTimestampBefore(cutoff);
        then(recommendationLogRepository).should().deleteByCreatedAtBefore(cutoff);
    }

    @Test
    void purgeOlderThanIsZeroWhenBothTablesAreEmpty() {
        LocalDateTime cutoff = LocalDateTime.of(2025, 1, 1, 0, 0);
        given(auditLogRepository.deleteByTimestampBefore(cutoff)).willReturn(0L);
        given(recommendationLogRepository.deleteByCreatedAtBefore(cutoff)).willReturn(0L);

        assertThat(job.purgeOlderThan(cutoff)).isZero();
    }
}
