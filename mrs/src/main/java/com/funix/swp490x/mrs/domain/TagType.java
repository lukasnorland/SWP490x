package com.funix.swp490x.mrs.domain;

/**
 * The metadata vocabularies a song can be tagged with.
 *
 * <p>{@link #TAGS} holds freeform provider descriptors — instruments, vibe
 * words, vocal style — which is what contextual LLM search reads (SRS 4.3).
 */
public enum TagType {

    GENRE("Genre"),
    MOOD("Mood"),
    ARTIST("Artist"),
    TAGS("Tags");

    private final String displayName;

    TagType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
