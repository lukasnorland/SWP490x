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

    /** Names the store in the admin UI, e.g. {@code s3://bucket/prefix}. */
    String describe();
}
