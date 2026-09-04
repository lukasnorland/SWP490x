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

/**
 * A catalog track. Maps the {@code song} table created by Flyway V1; the
 * schema is owned by the migration and JPA only validates against it.
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

    /** Public HTTPS mp3: a vendor CDN, or a copy we host ourselves. */
    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    /**
     * Wash colours sampled from the cover, as CSS rgba() values. Worked out on
     * the server because reading pixels in the browser needs a canvas, and a
     * canvas needs CORS headers that several vendor CDNs do not send.
     */
    @Column(name = "ambience_a", length = 40)
    private String ambienceA;

    @Column(name = "ambience_b", length = 40)
    private String ambienceB;

    /**
     * The cover the colours above were read from. Lets a changed cover be
     * recomputed, and a cover that could not be read be left alone rather than
     * retried by every import.
     */
    @Column(name = "ambience_source_url", length = 500)
    private String ambienceSourceUrl;

    private Integer bpm;

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
