package com.funix.swp490x.mrs.catalog;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * One-off walk of staged song JSON. Import then picks up the new ETags.
 * {@code --write} actually replaces files; without it this is a dry-run.
 *
 * <pre>
 * CatalogClassificationRewriter [--write] &lt;dir&gt;
 * CatalogClassificationRewriter [--write] --s3 [s3://bucket/prefix]
 * </pre>
 */
public final class CatalogClassificationRewriter {

    private static final String DEFAULT_S3 = "s3://mrs-133857166188-assets/song-data/";

    public static void main(String[] args) throws Exception {
        boolean write = false;
        boolean s3 = false;
        String target = null;
        for (String arg : args) {
            if ("--write".equals(arg)) {
                write = true;
            } else if ("--s3".equals(arg)) {
                s3 = true;
            } else if (!arg.startsWith("-")) {
                target = arg;
            }
        }

        if (s3) {
            String uri = target == null ? DEFAULT_S3 : target;
            try (S3Client client = s3Client()) {
                ParsedS3 parsed = parseS3(uri);
                run(new S3CatalogObjectStore(client, parsed.bucket, parsed.prefix), write);
            }
            return;
        }

        if (target == null) {
            System.err.println("Usage: CatalogClassificationRewriter [--write] <dir>");
            System.err.println("       CatalogClassificationRewriter [--write] --s3 [s3://bucket/prefix]");
            System.exit(2);
        }
        run(new LocalDirectoryCatalogObjectStore(java.nio.file.Path.of(target)), write);
    }

    static void run(CatalogObjectStore store, boolean write) {
        SongJsonMapper mapper = new SongJsonMapper();
        int scanned = 0;
        int rewritten = 0;
        Map<String, Integer> demoted = new LinkedHashMap<>();
        List<CatalogObject> objects = store.list();
        for (CatalogObject object : objects) {
            scanned++;
            String json = store.readJson(object.key());
            Optional<StagedSong> before = mapper.readStaged(json);
            Optional<String> next = mapper.reclassifyJson(json);
            if (next.isEmpty()) {
                continue;
            }
            rewritten++;
            before.ifPresent(song -> recordDemotions(song, mapper.readStaged(next.get()).orElse(null),
                    demoted));
            if (write) {
                store.putJson(object.key(), next.get());
            }
        }
        System.out.printf("source=%s scanned=%d rewritten=%d wrote=%s%n",
                store.describe(), scanned, rewritten, write);
        if (!demoted.isEmpty()) {
            System.out.println("names moved into tags (top 40):");
            demoted.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(40)
                    .forEach(e -> System.out.printf("  %5d  %s%n", e.getValue(), e.getKey()));
        }
    }

    private static void recordDemotions(StagedSong before, StagedSong after,
            Map<String, Integer> demoted) {
        if (before == null || after == null) {
            return;
        }
        List<String> afterTags = after.tags() == null ? List.of() : after.tags();
        List<String> beforeTags = before.tags() == null ? List.of() : before.tags();
        List<String> newcomers = new ArrayList<>();
        for (String tag : afterTags) {
            if (!containsIgnoreCase(beforeTags, tag)
                    && (containsIgnoreCase(before.genres(), tag)
                    || containsIgnoreCase(before.moods(), tag))) {
                newcomers.add(tag);
            }
        }
        for (String name : newcomers) {
            demoted.merge(name, 1, Integer::sum);
        }
    }

    private static boolean containsIgnoreCase(List<String> names, String needle) {
        if (names == null || needle == null) {
            return false;
        }
        for (String name : names) {
            if (name != null && name.equalsIgnoreCase(needle)) {
                return true;
            }
        }
        return false;
    }

    private static S3Client s3Client() {
        String region = System.getenv("AWS_DEFAULT_REGION");
        if (region == null || region.isBlank()) {
            region = System.getenv("AWS_REGION");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalStateException("Set AWS_DEFAULT_REGION or AWS_REGION for --s3");
        }
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    private static ParsedS3 parseS3(String uri) {
        URI parsed = URI.create(uri);
        if (!"s3".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null) {
            throw new IllegalArgumentException("Expected s3://bucket/prefix, got " + uri);
        }
        String path = parsed.getPath() == null ? "" : parsed.getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (!path.isEmpty() && !path.endsWith("/")) {
            path = path + "/";
        }
        return new ParsedS3(parsed.getHost(), path.isEmpty() ? "song-data/" : path);
    }

    private record ParsedS3(String bucket, String prefix) {
    }

    private CatalogClassificationRewriter() {
    }
}
