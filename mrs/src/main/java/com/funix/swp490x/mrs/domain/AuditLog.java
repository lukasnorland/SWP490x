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
 * An immutable record of an action that changed state (BR-10, NFR-SEC04). Maps
 * the {@code audit_log} table created by Flyway V1.
 *
 * <p>{@code actorId} is required, so only actions with a real actor belong here.
 * An unattended catalog sync is recorded in {@code catalog_import_run} instead.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    /** ADMIN ran the catalog import from P-06b. */
    public static final String ACTION_CATALOG_IMPORT = "CATALOG_IMPORT";

    public static final String ENTITY_CATALOG_IMPORT_RUN = "CATALOG_IMPORT_RUN";

    /** P-03a / P-03b playlist changes (FT-06). */
    public static final String ACTION_PLAYLIST_CREATE = "PLAYLIST_CREATE";

    public static final String ACTION_PLAYLIST_RENAME = "PLAYLIST_RENAME";

    public static final String ACTION_PLAYLIST_DELETE = "PLAYLIST_DELETE";

    public static final String ACTION_PLAYLIST_SONG_ADD = "PLAYLIST_SONG_ADD";

    public static final String ACTION_PLAYLIST_SONG_REMOVE = "PLAYLIST_SONG_REMOVE";

    public static final String ACTION_PLAYLIST_PUBLISH = "PLAYLIST_PUBLISH";

    public static final String ACTION_PLAYLIST_UNPUBLISH = "PLAYLIST_UNPUBLISH";
    /** Ownership moved to another account, e.g. when the owner lost the curator role. */
    public static final String ACTION_PLAYLIST_TRANSFER = "PLAYLIST_TRANSFER";

    public static final String ACTION_PLAYLIST_COLLABORATOR_ADD = "PLAYLIST_COLLABORATOR_ADD";

    public static final String ACTION_PLAYLIST_COLLABORATOR_REMOVE = "PLAYLIST_COLLABORATOR_REMOVE";

    public static final String ENTITY_PLAYLIST = "PLAYLIST";

    /** P-06d / UC-31 — operational parameters and the registered provider list. */
    public static final String ACTION_SETTINGS_UPDATE = "SETTINGS_UPDATE";

    public static final String ACTION_PROVIDER_CREATE = "PROVIDER_CREATE";

    public static final String ACTION_PROVIDER_DELETE = "PROVIDER_DELETE";

    public static final String ENTITY_SYSTEM_SETTING = "SYSTEM_SETTING";

    public static final String ENTITY_CATALOG_PROVIDER = "CATALOG_PROVIDER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    /** Before/after values, or an outcome summary. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String details;

    protected AuditLog() {
    }

    public AuditLog(Long actorId, String action, String entityType, Long entityId, String details) {
        this.actorId = actorId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getDetails() {
        return details;
    }
}
