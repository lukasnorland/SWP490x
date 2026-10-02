package com.funix.swp490x.mrs.catalog;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Staged catalog storage. Listing ETags separately from bodies supports incremental sync. */
public interface CatalogObjectStore {

    /**
     * Lists staged song JSON objects under the configured prefix.
     * @throws CatalogStoreException on an incomplete listing; partial results could trigger incorrect pruning
     */
    List<CatalogObject> list();

    /**
     * @return the UTF-8 body of one object
     * @throws CatalogStoreException when the object could not be read
     */
    String readJson(String key);

    /**
     * Reads a staged object, or returns empty if absent.
     * @throws CatalogStoreException when the store cannot be contacted
     */
    Optional<String> findJson(String key);

    /**
     * Writes staged JSON under its listing key.
     * @return the ETag to record on the catalog row
     * @throws CatalogStoreException when the object cannot be written
     */
    String putJson(String key, String json);

    /**
     * Deletes staged JSON; an absent object is allowed.
     * @throws CatalogStoreException when deletion fails
     */
    void deleteJson(String key);

    /** Returns the staging key for the external source id. */
    String stagingKey(String externalSourceId);

    /**
     * Writes audio or artwork with the known body length.
     * @throws CatalogStoreException when the object cannot be written
     */
    void putBinary(String key, String contentType, InputStream body, long length);

    /**
     * Removes one audio or cover object. A missing object is not an error.
     *
     * @throws CatalogStoreException when the object could not be deleted
     */
    void deleteBinary(String key);

    /**
     * Lists keys recursively for provider media cleanup.
     * @throws CatalogStoreException when the listing fails
     */
    List<String> listKeys(String keyPrefix);

    /** Returns {@code song-data/{audio|artwork}/<slug>/<id>.<ext>} for hosted media. */
    default String mediaKey(MediaKind kind, String providerSlug, String externalSourceId,
            String extension) {
        String ext = extension == null ? "" : extension;
        if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }
        ext = ext.toLowerCase(Locale.ROOT);
        return "song-data/" + kind.folder() + "/" + providerSlug + "/"
                + externalSourceId + "." + ext;
    }

    /** Names the store in the admin UI, e.g. {@code s3://bucket/prefix}. */
    String describe();
}
