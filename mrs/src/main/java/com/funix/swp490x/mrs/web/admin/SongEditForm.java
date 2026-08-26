package com.funix.swp490x.mrs.web.admin;

/**
 * Classification posted by the P-06b edit modal. Licensed identity (title,
 * artist, duration, ISRC, media) is not accepted — the service ignores it.
 */
public class SongEditForm {

    private Boolean explicit;
    private String genres;
    private String moods;
    private String tags;
    private int version;

    public Boolean getExplicit() {
        return explicit;
    }

    public void setExplicit(Boolean explicit) {
        this.explicit = explicit;
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

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
