package com.funix.swp490x.mrs.catalog;

import org.springframework.web.multipart.MultipartFile;

/**
 * One song's worth of files and metadata from the P-06b Add Song form.
 *
 * <p>Field names match {@code drafts[i].*} so Spring can bind a batch of
 * sections from one multipart request. {@code externalSourceId} is not a
 * field: the service assigns it.
 */
public class SongDraftForm {

    private MultipartFile audio;
    private MultipartFile cover;
    private String sourceProvider;
    private String title;
    private String artist;
    private Integer duration;
    private Integer bpm;
    private Boolean isExplicit;
    private String isrc;
    private String genres;
    private String moods;
    private String tags;

    public MultipartFile getAudio() {
        return audio;
    }

    public void setAudio(MultipartFile audio) {
        this.audio = audio;
    }

    public MultipartFile getCover() {
        return cover;
    }

    public void setCover(MultipartFile cover) {
        this.cover = cover;
    }

    public String getSourceProvider() {
        return sourceProvider;
    }

    public void setSourceProvider(String sourceProvider) {
        this.sourceProvider = sourceProvider;
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

    public Integer getBpm() {
        return bpm;
    }

    public void setBpm(Integer bpm) {
        this.bpm = bpm;
    }

    public Boolean getIsExplicit() {
        return isExplicit;
    }

    public void setIsExplicit(Boolean isExplicit) {
        this.isExplicit = isExplicit;
    }

    public String getIsrc() {
        return isrc;
    }

    public void setIsrc(String isrc) {
        this.isrc = isrc;
    }

    public String getGenres() {
        return genres;
    }

    public void setGenres(String genres) {
        this.genres = genres;
    }

    public String getMoods() {
        return moods;
    }

    public void setMoods(String moods) {
        this.moods = moods;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }
}
