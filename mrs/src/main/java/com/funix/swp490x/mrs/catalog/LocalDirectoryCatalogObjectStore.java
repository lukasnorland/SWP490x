package com.funix.swp490x.mrs.catalog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads the staged catalog from a directory of {@code *.json} files, so the
 * whole import path runs with no AWS credentials — a fresh checkout can import
 * {@code scripts/data}, and tests can drive the sync from fixtures.
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
        try {
            return Files.readString(resolve(key), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CatalogStoreException("Could not read " + key + " under " + describe(), e);
        }
    }

    @Override
    public String describe() {
        return root.toAbsolutePath().toString();
    }

    /**
     * Keys are plain file names here, so anything that would escape the root
     * (a separator or {@code ..}) is a bug or an attempt at traversal.
     */
    private Path resolve(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root.normalize())) {
            throw new CatalogStoreException("Key escapes the catalog directory: " + key);
        }
        return resolved;
    }

    private static String md5(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is required of every JVM", e);
        }
    }
}
