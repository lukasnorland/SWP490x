package com.funix.swp490x.mrs.catalog;

import java.util.List;

/**
 * The staged song JSON, wherever it lives.
 *
 * <p>Listing is deliberately separate from reading: {@link #list()} carries an
 * ETag per object, which lets a sync work out what changed without downloading
 * a single body. Over an untouched prefix that makes a sync one listing and no
 * reads at all.
 */
public interface CatalogObjectStore {

    /**
     * Every object under the configured prefix.
     *
     * @throws CatalogStoreException when the listing could not be completed;
     *     a partial listing is never returned, since it would look to a sync
     *     like the missing objects had been deleted
     */
    List<CatalogObject> list();

    /**
     * @return the UTF-8 body of one object
     * @throws CatalogStoreException when the object could not be read
     */
    String readJson(String key);

    /**
     * Writes (or overwrites) one staged song object.
     *
     * <p>The key must be the same shape {@link #list()} returns for that
     * object, so a put is immediately visible to the next ETag diff.
     *
     * @throws CatalogStoreException when the object could not be written
     */
    void putJson(String key, String json);

    /**
     * The key under which a song with this external id is staged — the same
     * form {@link #list()} would report after a put.
     */
    String stagingKey(String externalSourceId);

    /** Names the store in the admin UI, e.g. {@code s3://bucket/prefix}. */
    String describe();
}
