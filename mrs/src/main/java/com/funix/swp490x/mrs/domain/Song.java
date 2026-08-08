package com.funix.swp490x.mrs.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A catalog track. Maps the {@code song} table created by Flyway V1 and
 * extended by V3; the schema is owned by the migrations and JPA only validates
 * against it.
 *
 * <p>{@code (sourceProvider, externalSourceId)} is unique (DC-04), which is
 * what lets an import update in place rather than duplicating a song.
 */
@Entity
@Table(name = "song")
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** An import row without one is skipped (SC-05). */
    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 255)
    private String artist;

    /** Seconds; positive when present. */
    private Integer duration;

    /** Must name a registered provider (SC-05). */
    @Column(name = "source_provider", nullable = false, length = 100)
    private String sourceProvider;

    @Column(name = "external_source_id", length = 100)
    private String externalSourceId;

    /** 0-100 snapshot; null means never synced (BR-08). */
    @Column(name = "spotify_popularity")
    private Byte spotifyPopularity;

    @Column(name = "popularity_synced_at")
    private LocalDateTime popularitySyncedAt;

    /** Licensed audio held in our own bucket, exported pre-signed (BR-13). */
    @Column(name = "audio_s3_key", length = 500)
    private String audioS3Key;

    /** The provider's public CDN mp3, as staged by the import. */
    @Column(name = "preview_url", length = 500)
    private String previewUrl;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    private Integer bpm;

    @Column(name = "energy_level", length = 20)
    private String energyLevel;

    @Column(name = "has_vocals")
    private Boolean hasVocals;

    @Column(name = "is_explicit")
    private Boolean explicit;

    @Column(length = 20)
    private String isrc;

    /**
     * Hash of the staged object this row was last built from. A sync compares it
     * against the ETag in the S3 listing and skips the object when they match,
     * so an unchanged prefix is never downloaded.
     */
    @Column(name = "source_etag", length = 64)
    private String sourceEtag;

    /**
     * Optimistic locking (DC-02). A concurrent edit makes the import skip the
     * row and report it rather than overwriting the edit (BR-06).
     */
    @Version
    @Column(nullable = false)
    private int version;

    /**
     * A song needs at least one tag to appear in filtered results (DC-03).
     *
     * <p>Cascades persist so a brand-new tag saves with the song, but never
     * remove, since tags are shared vocabulary: detaching a tag from one song
     * must not delete it from the dictionary.
     */
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(name = "song_tag",
            joinColumns = @JoinColumn(name = "song_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public String getSourceProvider() {
        return sourceProvider;
    }

    public void setSourceProvider(String sourceProvider) {
        this.sourceProvider = sourceProvider;
    }

    public String getExternalSourceId() {
        return externalSourceId;
    }

    public void setExternalSourceId(String externalSourceId) {
        this.externalSourceId = externalSourceId;
    }

    public Byte getSpotifyPopularity() {
        return spotifyPopularity;
    }

    public void setSpotifyPopularity(Byte spotifyPopularity) {
        this.spotifyPopularity = spotifyPopularity;
    }

    public LocalDateTime getPopularitySyncedAt() {
        return popularitySyncedAt;
    }

    public void setPopularitySyncedAt(LocalDateTime popularitySyncedAt) {
        this.popularitySyncedAt = popularitySyncedAt;
    }

    public String getAudioS3Key() {
        return audioS3Key;
    }

    public void setAudioS3Key(String audioS3Key) {
        this.audioS3Key = audioS3Key;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public Integer getBpm() {
        return bpm;
    }

    public void setBpm(Integer bpm) {
        this.bpm = bpm;
    }

    public String getEnergyLevel() {
        return energyLevel;
    }

    public void setEnergyLevel(String energyLevel) {
        this.energyLevel = energyLevel;
    }

    public Boolean getHasVocals() {
        return hasVocals;
    }

    public void setHasVocals(Boolean hasVocals) {
        this.hasVocals = hasVocals;
    }

    public Boolean getExplicit() {
        return explicit;
    }

    public void setExplicit(Boolean explicit) {
        this.explicit = explicit;
    }

    public String getIsrc() {
        return isrc;
    }

    public void setIsrc(String isrc) {
        this.isrc = isrc;
    }

    public String getSourceEtag() {
        return sourceEtag;
    }

    public void setSourceEtag(String sourceEtag) {
        this.sourceEtag = sourceEtag;
    }

    public int getVersion() {
        return version;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }
}
