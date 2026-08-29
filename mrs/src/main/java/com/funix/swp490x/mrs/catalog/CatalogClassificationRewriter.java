package com.funix.swp490x.mrs.catalog;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * One-off walk of a directory of staged song JSON. Import then picks up the
 * new ETags. {@code --write} actually replaces files; without it this is a
 * dry-run.
 */
public final class CatalogClassificationRewriter {

    public static void main(String[] args) throws Exception {
        boolean write = false;
        Path dir = null;
        for (String arg : args) {
            if ("--write".equals(arg)) {
                write = true;
            } else if (!arg.startsWith("-")) {
                dir = Path.of(arg);
            }
        }
        if (dir == null || !Files.isDirectory(dir)) {
            System.err.println("Usage: CatalogClassificationRewriter [--write] <dir>");
            System.exit(2);
        }

        SongJsonMapper mapper = new SongJsonMapper();
        int scanned = 0;
        int rewritten = 0;
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                scanned++;
                String json = Files.readString(path, StandardCharsets.UTF_8);
                var next = mapper.reclassifyJson(json);
                if (next.isEmpty()) {
                    continue;
                }
                rewritten++;
                if (write) {
                    Files.writeString(path, next.get(), StandardCharsets.UTF_8);
                }
            }
        }
        System.out.printf("scanned=%d rewritten=%d wrote=%s%n", scanned, rewritten, write);
    }

    private CatalogClassificationRewriter() {
    }
}
