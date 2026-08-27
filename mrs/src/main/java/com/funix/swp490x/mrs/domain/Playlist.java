package com.funix.swp490x.mrs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

/**
 * A curated ordered set of songs. Maps the {@code playlist} table created by
 * Flyway V1; the schema is owned by the migration and JPA only validates
 * against it.
 *
 * <p>Ownership and authorship are plain id columns rather than associations:
 * every screen that shows a playlist needs the modifier's display name, never
 * the whole account, and {@code spring.jpa.open-in-view=false} would make a
 * lazy association fail at render time.
 */
@Entity
@Table(name = "playlist")
public class Playlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Renameable only while Draft (DC-08). */
    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaylistStatus status = PlaylistStatus.DRAFT;

    /** Reassignable by ADMIN only (BR-14). */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_modified_at", nullable = false)
    private LocalDateTime lastModifiedAt;

    @Column(name = "last_modified_by", nullable = false)
    private Long lastModifiedBy;

    /** Set on publish, which requires at least one song (BR-05). */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** Clone lineage only (DC-07). */
    @Column(name = "source_playlist_id")
    private Long sourcePlaylistId;

    /** Optimistic locking (DC-02, BR-06). */
    @Version
    @Column(nullable = false)
    private int version;

    protected Playlist() {
    }

    public Playlist(String name, Long actorId) {
        this.name = name;
        this.ownerId = actorId;
        this.createdBy = actorId;
        this.lastModifiedBy = actorId;
    }

    /**
     * The columns default to CURRENT_TIMESTAMP, but an insert from JPA sends
     * every field explicitly and a null would be rejected outright rather than
     * defaulted.
     */
    @PrePersist
    void stampTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (lastModifiedAt == null) {
            lastModifiedAt = now;
        }
    }

    /** Records who last changed the playlist or its contents (FT-06 AC-06). */
    public void touch(Long actorId) {
        this.lastModifiedBy = actorId;
        this.lastModifiedAt = LocalDateTime.now();
    }

    public boolean isPublished() {
        return status == PlaylistStatus.PUBLISHED;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PlaylistStatus getStatus() {
        return status;
    }

    public void setStatus(PlaylistStatus status) {
        this.status = status;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastModifiedAt() {
        return lastModifiedAt;
    }

    public Long getLastModifiedBy() {
        return lastModifiedBy;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Long getSourcePlaylistId() {
        return sourcePlaylistId;
    }

    public void setSourcePlaylistId(Long sourcePlaylistId) {
        this.sourcePlaylistId = sourcePlaylistId;
    }

    public int getVersion() {
        return version;
    }
}
