package com.funix.swp490x.mrs.catalog;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

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
                    // Without a delimiter, ListObjectsV2 is recursive. Media
                    // under song-data/audio/ and any extra vendor trees then
                    // appear as songs. The nested copy is applied after the
                    // real object and can wipe fields the copy does not have.
                    .delimiter("/")
                    .build();

            for (ListObjectsV2Response page : s3.listObjectsV2Paginator(request)) {
                page.contents().stream()
                        // A "directory marker" is a zero-byte object at the
                        // prefix itself; it carries no song.
                        .filter(o -> o.size() != null && o.size() > 0)
                        .filter(o -> isStagedSongKey(prefix, o.key()))
                        .forEach(o -> objects.add(new CatalogObject(o.key(), unquote(o.eTag()))));
            }
        } catch (RuntimeException e) {
            // ProcessCredentialsProvider throws IllegalStateException when
            // `aws login` has expired — that is not an SdkException.
            throw CatalogStoreException.of("Could not list " + describe(), e);
        }
        return objects;
    }

    @Override
    public String readJson(String key) {
        return findJson(key).orElseThrow(() ->
                new CatalogStoreException("Could not read s3://" + bucket + "/" + key
                        + ": object is missing"));
    }

    @Override
    public Optional<String> findJson(String key) {
        try {
            return Optional.of(s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asUtf8String());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw CatalogStoreException.of("Could not read s3://" + bucket + "/" + key, e);
        } catch (RuntimeException e) {
            throw CatalogStoreException.of("Could not read s3://" + bucket + "/" + key, e);
        }
    }

    @Override
    public String putJson(String key, String json) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("application/json")
                    .build();
            PutObjectResponse response = s3.putObject(request,
                    RequestBody.fromString(json, StandardCharsets.UTF_8));
            return unquote(response.eTag());
        } catch (RuntimeException e) {
            throw CatalogStoreException.of("Could not write s3://" + bucket + "/" + key, e);
        }
    }

    @Override
    public void deleteJson(String key) {
        deleteObject(key);
    }

    @Override
    public void deleteBinary(String key) {
        deleteObject(key);
    }

    @Override
    public List<String> listKeys(String keyPrefix) {
        List<String> keys = new ArrayList<>();
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(keyPrefix)
                    .build();
            for (ListObjectsV2Response page : s3.listObjectsV2Paginator(request)) {
                page.contents().stream()
                        .filter(o -> o.size() != null && o.size() > 0)
                        .forEach(o -> keys.add(o.key()));
            }
        } catch (RuntimeException e) {
            throw CatalogStoreException.of("Could not list s3://" + bucket + "/" + keyPrefix, e);
        }
        return keys;
    }

    private void deleteObject(String key) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            s3.deleteObject(request);
        } catch (RuntimeException e) {
            throw CatalogStoreException.of("Could not delete s3://" + bucket + "/" + key, e);
        }
    }

    @Override
    public void putBinary(String key, String contentType, InputStream body, long length) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();
            s3.putObject(request, RequestBody.fromInputStream(body, length));
        } catch (RuntimeException e) {
            throw CatalogStoreException.of("Could not write s3://" + bucket + "/" + key, e);
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

    /**
     * Staged songs live at {@code {prefix}{externalSourceId}.json}, the same
     * one-level listing {@link LocalDirectoryCatalogObjectStore} uses. JSON
     * under a subfolder is media metadata or a duplicate vendor tree, not a
     * catalog object of its own.
     */
    static boolean isStagedSongKey(String prefix, String key) {
        if (key == null || prefix == null || !key.startsWith(prefix) || !key.endsWith(".json")) {
            return false;
        }
        String name = key.substring(prefix.length());
        return !name.isEmpty() && name.indexOf('/') < 0;
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
