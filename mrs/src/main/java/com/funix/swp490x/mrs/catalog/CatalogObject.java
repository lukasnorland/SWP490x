package com.funix.swp490x.mrs.catalog;

/**
 * One staged song object as it appears in a listing, before its body is read.
 *
 * @param key full object key, e.g. {@code song-data/2f02e814-....json}
 * @param etag hash of the object's content, compared against
 *     {@code song.source_etag} to decide whether a read is needed
 */
public record CatalogObject(String key, String etag) {

    /**
     * The song id this object is expected to carry, taken from the file name
     * that the staging scripts use ({@code <externalSourceId>.json}).
     *
     * <p>Knowing the id before reading is what makes the diff listing-only. A
     * key not following the convention yields {@code null}, and the caller then
     * has to read the object to find out what is in it.
     */
    public String externalSourceIdHint() {
        int slash = key.lastIndexOf('/');
        String name = slash < 0 ? key : key.substring(slash + 1);
        if (!name.endsWith(".json")) {
            return null;
        }
        String id = name.substring(0, name.length() - ".json".length());
        return id.isBlank() ? null : id;
    }
}
