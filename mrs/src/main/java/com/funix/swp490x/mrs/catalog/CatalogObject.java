package com.funix.swp490x.mrs.catalog;

/**
 * A staged song entry returned by the object listing.
 * @param key full object key
 * @param etag change identifier compared with {@code song.source_etag}
 */
public record CatalogObject(String key, String etag) {

    /** Extracts the external source id from the JSON filename, or null when it cannot be inferred. */
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
