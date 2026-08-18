package com.funix.swp490x.mrs.catalog;

/**
 * Company-hosted media that travels with a staged song. Audio and artwork live
 * under separate prefixes so the catalog listing (JSON only) never sees them.
 */
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
