package com.funix.swp490x.mrs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * One execution of the catalog import, kept so P-06c can report what the last
 * run did and BR-10 has a trail for unattended runs.
 *
 * <p>Separate from {@code audit_log}, which requires an actor: a scheduled sync
 * has none. A {@link ImportTrigger#MANUAL} run also writes {@code audit_log}
 * with the ADMIN who pressed the button.
 */
@Entity
@Table(name = "catalog_import_run")
public class CatalogImportRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private ImportTrigger triggerType;

    /** Null for STARTUP and SCHEDULED runs, which have no human actor. */
    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** Null while the run is still in flight. */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "objects_listed", nullable = false)
    private int objectsListed;

    @Column(nullable = false)
    private int added;

    @Column(nullable = false)
    private int updated;

    @Column(nullable = false)
    private int skipped;

    @Column(length = 500)
    private String error;

    protected CatalogImportRun() {
    }

    public CatalogImportRun(ImportTrigger triggerType, Long actorId) {
        this.triggerType = triggerType;
        this.actorId = actorId;
        this.startedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public ImportTrigger getTriggerType() {
        return triggerType;
    }

    public Long getActorId() {
        return actorId;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public int getObjectsListed() {
        return objectsListed;
    }

    public void setObjectsListed(int objectsListed) {
        this.objectsListed = objectsListed;
    }

    public int getAdded() {
        return added;
    }

    public void setAdded(int added) {
        this.added = added;
    }

    public int getUpdated() {
        return updated;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean isFailed() {
        return error != null;
    }
}
