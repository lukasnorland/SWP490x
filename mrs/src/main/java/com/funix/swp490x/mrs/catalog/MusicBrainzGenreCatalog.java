package com.funix.swp490x.mrs.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cached MusicBrainz genre names shipped on the classpath so lookup does not
 * hit musicbrainz.org at runtime. The snapshot is supplementary data under
 * CC BY-NC-SA 3.0; see {@code catalog/musicbrainz-genres.json}.
 */
public final class MusicBrainzGenreCatalog {

    private static final String RESOURCE = "/catalog/musicbrainz-genres.json";
    private static final MusicBrainzGenreCatalog INSTANCE = load();

    private final List<String> rawNames;

    private MusicBrainzGenreCatalog(List<String> rawNames) {
        this.rawNames = List.copyOf(rawNames);
    }

    public static MusicBrainzGenreCatalog get() {
        return INSTANCE;
    }

    public List<String> rawNames() {
        return rawNames;
    }

    public int size() {
        return rawNames.size();
    }

    private static MusicBrainzGenreCatalog load() {
        try (InputStream in = MusicBrainzGenreCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + RESOURCE);
            }
            JsonNode root = JsonMapper.builder().build().readTree(in.readAllBytes());
            JsonNode genres = root.get("genres");
            if (genres == null || !genres.isArray()) {
                throw new IllegalStateException(RESOURCE + " has no genres array");
            }
            List<String> names = new ArrayList<>();
            for (JsonNode node : genres) {
                if (node == null || !node.isString()) {
                    continue;
                }
                String name = node.asString().trim();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
            if (names.isEmpty()) {
                throw new IllegalStateException(RESOURCE + " listed no genres");
            }
            return new MusicBrainzGenreCatalog(names);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Could not read " + RESOURCE, e);
        }
    }
}
