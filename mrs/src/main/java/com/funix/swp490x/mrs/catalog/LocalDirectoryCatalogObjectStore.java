package com.funix.swp490x.mrs.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads the staged catalog from a directory of {@code *.json} files, so the
 * whole import path runs with no AWS credentials. Tests drive the sync from
 * fixtures.
 *
 * <p>The ETag is an MD5 of the file content, matching what S3 reports for a
 * single-part upload. The change detection therefore behaves identically here
 * and against the real bucket.
 */
public class LocalDirectoryCatalogObjectStore implements CatalogObjectStore {

    private final Path root;

    public LocalDirectoryCatalogObjectStore(Path root) {
        this.root = root;
    }

    @Override
    public List<CatalogObject> list() {
        if (!Files.exists(root)) {
            return List.of();
        }
        if (!Files.isDirectory(root)) {
            throw new CatalogStoreException("Not a directory: " + root.toAbsolutePath());
        }
        try (Stream<Path> files = Files.list(root)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .map(p -> new CatalogObject(p.getFileName().toString(), md5(p)))
                    .toList();
        } catch (IOException e) {
            throw new CatalogStoreException("Could not list " + describe(), e);
        }
    }

    @Override
    public String readJson(String key) {
        return findJson(key).orElseThrow(() ->
                new CatalogStoreException("Could not read " + key + " under " + describe()
                        + ": object is missing"));
    }

    @Override
    public Optional<String> findJson(String key) {
        Path target = resolve(key);
        if (!Files.exists(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(target, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new CatalogStoreException("Could not read " + key + " under " + describe(), e);
        }
    }

    @Override
    public String putJson(String key, String json) {
        try {
            Path target = resolve(key);
            Files.createDirectories(parentOf(target));
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            Files.write(target, bytes);
            return md5(bytes);
        } catch (IOException e) {
            throw new CatalogStoreException("Could not write " + key + " under " + describe(), e);
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
        Path start = resolve(keyPrefix);
        if (!Files.exists(start)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(start)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new CatalogStoreException("Could not list " + keyPrefix + " under " + describe(), e);
        }
    }

    private void deleteObject(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new CatalogStoreException("Could not delete " + key + " under " + describe(), e);
        }
    }

    @Override
    public void putBinary(String key, String contentType, InputStream body, long length) {
        try {
            Path target = resolve(key);
            Files.createDirectories(parentOf(target));
            Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new CatalogStoreException("Could not write " + key + " under " + describe(), e);
        }
    }

    @Override
    public String stagingKey(String externalSourceId) {
        return externalSourceId + ".json";
    }

    @Override
    public String describe() {
        return root.toAbsolutePath().toString();
    }

    /**
     * Keys that contain a separator (media under {@code song-data/audio/…})
     * stay inside the root; anything that would escape it
     * ({@code ..}) is a bug or an attempt at traversal.
     */
    private Path resolve(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root.normalize())) {
            throw new CatalogStoreException("Key escapes the catalog directory: " + key);
        }
        return resolved;
    }

    private Path parentOf(Path target) {
        Path parent = target.getParent();
        return parent == null ? root : parent;
    }

    private static String md5(Path file) {
        try {
            return md5(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String md5(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is required of every JVM", e);
        }
    }
}
