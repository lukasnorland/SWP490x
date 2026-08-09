package com.funix.swp490x.mrs.catalog;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Reads the staged catalog from the S3 assets bucket.
 *
 * <p>{@code ListObjectsV2} already reports an ETag per object, so working out
 * what changed costs one request per 1,000 keys and no object reads.
 */
public class S3CatalogObjectStore implements CatalogObjectStore {

    private final S3Client s3;
    private final String bucket;
    private final String prefix;

    public S3CatalogObjectStore(S3Client s3, String bucket, String prefix) {
        this.s3 = s3;
        this.bucket = bucket;
        this.prefix = prefix;
    }

    @Override
    public List<CatalogObject> list() {
        List<CatalogObject> objects = new ArrayList<>();
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .build();

            for (ListObjectsV2Response page : s3.listObjectsV2Paginator(request)) {
                page.contents().stream()
                        // A "directory marker" is a zero-byte object at the
                        // prefix itself; it carries no song.
                        .filter(o -> o.size() != null && o.size() > 0)
                        .filter(o -> o.key().endsWith(".json"))
                        .forEach(o -> objects.add(new CatalogObject(o.key(), unquote(o.eTag()))));
            }
        } catch (SdkException e) {
            throw new CatalogStoreException("Could not list " + describe(), e);
        }
        return objects;
    }

    @Override
    public String readJson(String key) {
        try {
            return s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asUtf8String();
        } catch (SdkException e) {
            throw new CatalogStoreException("Could not read s3://" + bucket + "/" + key, e);
        }
    }

    @Override
    public void putJson(String key, String json) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("application/json")
                    .build();
            s3.putObject(request, RequestBody.fromString(json, StandardCharsets.UTF_8));
        } catch (SdkException e) {
            throw new CatalogStoreException("Could not write s3://" + bucket + "/" + key, e);
        }
    }

    @Override
    public String stagingKey(String externalSourceId) {
        return prefix + externalSourceId + ".json";
    }

    @Override
    public String describe() {
        return "s3://" + bucket + "/" + prefix;
    }

    /** S3 returns the ETag wrapped in literal double quotes. */
    private static String unquote(String etag) {
        if (etag == null) {
            return null;
        }
        String trimmed = etag.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
