package com.funix.swp490x.mrs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One P-02 Search submit (FT-04, BR-07). Maps {@code recommendation_log};
 * Flyway owns the table.
 */
@Entity
@Table(name = "recommendation_log")
public class RecommendationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "query_text", columnDefinition = "TEXT")
    private String queryText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interpreted_filters")
    private String interpretedFilters;

    @Column(name = "llm_used", nullable = false)
    private boolean llmUsed;

    @Column(name = "llm_succeeded")
    private Boolean llmSucceeded;

    @Column(name = "result_count", nullable = false)
    private int resultCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected RecommendationLog() {
    }

    public RecommendationLog(Long userId, String queryText, String interpretedFilters,
            boolean llmUsed, Boolean llmSucceeded, int resultCount) {
        this.userId = userId;
        this.queryText = queryText;
        this.interpretedFilters = interpretedFilters;
        this.llmUsed = llmUsed;
        this.llmSucceeded = llmSucceeded;
        this.resultCount = resultCount;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getQueryText() {
        return queryText;
    }

    public String getInterpretedFilters() {
        return interpretedFilters;
    }

    public boolean isLlmUsed() {
        return llmUsed;
    }

    public Boolean getLlmSucceeded() {
        return llmSucceeded;
    }

    public int getResultCount() {
        return resultCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
