package com.funix.swp490x.mrs.catalog;

/** Hosted audio and artwork use separate prefixes from staged song JSON. */
public enum MediaKind {

    AUDIO("audio"),
    ARTWORK("artwork");

    private final String folder;

    MediaKind(String folder) {
        this.folder = folder;
    }

    /** Path segment under {@code song-data/}, e.g. {@code audio}. */
    public String folder() {
        return folder;
    }
}
