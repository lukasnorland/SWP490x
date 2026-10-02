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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Catalog track, unique by provider and external source id for update-in-place import (UC-28). */
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

    /** Required positive duration in seconds. */
    private Integer duration;

    /** Must name a registered provider (SC-05). */
    @Column(name = "source_provider", nullable = false, length = 100)
    private String sourceProvider;

    @Column(name = "external_source_id", length = 100)
    private String externalSourceId;

    /** Stored audio URL for vendor-hosted or company-hosted playback; may be absent (DC-13). */
    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    /** Server-sampled CSS cover colors for the shell background. */
    @Column(name = "ambience_a", length = 40)
    private String ambienceA;

    @Column(name = "ambience_b", length = 40)
    private String ambienceB;

    /** Cover URL last sampled; prevents repeated attempts until the URL changes. */
    @Column(name = "ambience_source_url", length = 500)
    private String ambienceSourceUrl;

    private Integer bpm;

    @Column(name = "is_explicit")
    private Boolean explicit;

    @Column(length = 20)
    private String isrc;

    /** ETag last applied to this row; matching staged objects are skipped during sync. */
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
     * Shared tag membership for metadata search (DC-03).
     * Persist new tags, but never cascade deletion of shared dictionary entries.
     */
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(name = "song_tag",
            joinColumns = @JoinColumn(name = "song_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getAmbienceA() {
        return ambienceA;
    }

    public void setAmbienceA(String ambienceA) {
        this.ambienceA = ambienceA;
    }

    public String getAmbienceB() {
        return ambienceB;
    }

    public void setAmbienceB(String ambienceB) {
        this.ambienceB = ambienceB;
    }

    public String getAmbienceSourceUrl() {
        return ambienceSourceUrl;
    }

    public void setAmbienceSourceUrl(String ambienceSourceUrl) {
        this.ambienceSourceUrl = ambienceSourceUrl;
    }

    public Integer getBpm() {
        return bpm;
    }

    public void setBpm(Integer bpm) {
        this.bpm = bpm;
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

    public String getGenreNames() {
        return tagNames(TagType.GENRE);
    }

    public String getMoodNames() {
        return tagNames(TagType.MOOD);
    }

    public String getFreeformTagNames() {
        return tagNames(TagType.TAGS);
    }

    /** Genre badges for the catalog table (JSON {@code genres}). */
    public List<Tag> getGenreTags() {
        return tagsOf(TagType.GENRE);
    }

    /** Mood badges for the catalog table (JSON {@code moods}). */
    public List<Tag> getMoodTags() {
        return tagsOf(TagType.MOOD);
    }

    /** Artist badges for P-02 filters and chips. */
    public List<Tag> getArtistTags() {
        return tagsOf(TagType.ARTIST);
    }

    /** Freeform tag badges for the catalog table (JSON {@code tags}). */
    public List<Tag> getFreeformTags() {
        return tagsOf(TagType.TAGS);
    }

    /** Comma-separated names of one vocabulary, for the P-06b edit modal. */
    public String tagNames(TagType type) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        return tags.stream()
                .filter(tag -> tag.getType() == type)
                .map(Tag::getName)
                .collect(Collectors.joining(", "));
    }

    private List<Tag> tagsOf(TagType type) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .filter(tag -> tag.getType() == type)
                .sorted(Comparator.comparing(Tag::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
