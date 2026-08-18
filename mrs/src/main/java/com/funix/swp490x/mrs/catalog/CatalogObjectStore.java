package com.funix.swp490x.mrs.catalog;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

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

    /**
     * Writes (or overwrites) one binary object — audio or cover art.
     *
     * <p>{@code length} is the known size of {@code body}. S3 needs it up front;
     * a local store uses it only as a sanity check.
     *
     * @throws CatalogStoreException when the object could not be written
     */
    void putBinary(String key, String contentType, InputStream body, long length);

    /**
     * Canonical key for company-hosted media, matching the CloudFront prefixes
     * in {@code infra/cloudfront-audio.yaml}:
     * {@code song-data/{audio|artwork}/<slug>/<id>.<ext>}.
     */
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
