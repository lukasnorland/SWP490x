package com.funix.swp490x.mrs.catalog;

import com.funix.swp490x.mrs.catalog.CatalogProperties.CoverArt;
import com.funix.swp490x.mrs.repository.SongRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Works out the shell's background wash for each song by reading its cover.
 *
 * <p>The browser cannot do this. Sampling an image into a canvas taints it
 * unless the host sends CORS headers, and several vendor CDNs do not — the
 * OneOff covers on {@code d3tw6r1ume3z4m}, {@code dtlqw9i1yvzdo} and
 * {@code wpfvk11.mep-cdn.net} send none at all, so more than half of that
 * catalog never got a wash. A server has no such restriction, so the colours
 * are read once and stored on the song.
 *
 * <p>An import fills in a bounded batch rather than the whole catalog, so a
 * scheduled sync stays short; whatever is left is picked up by the next run.
 */
@Service
public class CoverAmbienceService {

    private static final Logger log = LoggerFactory.getLogger(CoverAmbienceService.class);

    /** Some CDNs answer the default Java agent with a redirect to nowhere. */
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; MRS/1.0; +catalog cover sampling)";

    /** Rows per write transaction, matching the import's own chunking. */
    private static final int WRITE_BATCH = 200;

    private final SongRepository songRepository;
    private final CoverAmbienceWriter writer;
    private final CatalogProperties properties;
    private final HttpClient http;

    public CoverAmbienceService(SongRepository songRepository,
            CoverAmbienceWriter writer,
            CatalogProperties properties) {
        this.songRepository = songRepository;
        this.writer = writer;
        this.properties = properties;
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(properties.getCoverArt().getTimeout())
                .build();
        // Decoding several covers at once should not go through a shared
        // on-disk cache; these images are small enough to stay in memory.
        ImageIO.setUseCache(false);
    }

    /**
     * Gives wash colours to songs whose cover has not been sampled yet, and to
     * songs whose cover has changed since it was.
     *
     * @param pool the import's bounded reader pool, reused so cover reads
     *     cannot widen past what the run already allows itself
     * @return how many songs came away with colours
     */
    public int fillMissing(ExecutorService pool) {
        CoverArt config = properties.getCoverArt();
        if (!config.isEnabled() || config.getBatchSize() <= 0) {
            return 0;
        }

        List<Object[]> pending = songRepository.findCoversNeedingAmbience(
                PageRequest.of(0, config.getBatchSize()));
        if (pending.isEmpty()) {
            return 0;
        }

        // Compilations and singles off one release share art, so reading each
        // distinct cover once saves a good share of the requests.
        Set<String> distinct = new LinkedHashSet<>();
        for (Object[] row : pending) {
            distinct.add((String) row[1]);
        }

        log.info("Cover ambience: reading {} cover(s) for {} song(s)", distinct.size(), pending.size());
        Map<String, Optional<CoverAmbience>> colours = readAll(pool, distinct);

        List<CoverAmbienceWriter.Update> updates = new ArrayList<>(pending.size());
        int filled = 0;
        for (Object[] row : pending) {
            Long id = (Long) row[0];
            String coverUrl = (String) row[1];
            CoverAmbience found = colours.getOrDefault(coverUrl, Optional.empty()).orElse(null);
            if (found != null) {
                filled++;
            }
            // The source url is recorded either way, so a cover that cannot be
            // read is not queued again by every later import.
            updates.add(new CoverAmbienceWriter.Update(id,
                    found == null ? null : found.a(),
                    found == null ? null : found.b(),
                    coverUrl));
        }

        for (int from = 0; from < updates.size(); from += WRITE_BATCH) {
            writer.write(updates.subList(from, Math.min(from + WRITE_BATCH, updates.size())));
        }
        log.info("Cover ambience: {} of {} song(s) now have a wash", filled, pending.size());
        return filled;
    }

    private Map<String, Optional<CoverAmbience>> readAll(ExecutorService pool, Set<String> urls) {
        Map<String, CompletableFuture<Optional<CoverAmbience>>> futures = new HashMap<>();
        for (String url : urls) {
            futures.put(url, CompletableFuture.supplyAsync(() -> read(url), pool));
        }
        Map<String, Optional<CoverAmbience>> resolved = new HashMap<>();
        futures.forEach((url, future) -> resolved.put(url, future.join()));
        return resolved;
    }

    /**
     * Reads one cover. Never throws: a cover that cannot be fetched or decoded
     * simply leaves that song without a wash, which is the state it was in
     * before, and must not fail the import that asked for it.
     */
    public Optional<CoverAmbience> read(String coverUrl) {
        CoverArt config = properties.getCoverArt();
        URI uri;
        try {
            uri = URI.create(coverUrl);
        } catch (IllegalArgumentException e) {
            log.debug("Cover ambience: {} is not a usable URL", coverUrl);
            return Optional.empty();
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            log.debug("Cover ambience: refusing non-http cover {}", coverUrl);
            return Optional.empty();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(config.getTimeout())
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "image/*")
                    .GET()
                    .build();
            HttpResponse<InputStream> response =
                    http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                log.debug("Cover ambience: {} answered {}", coverUrl, response.statusCode());
                return Optional.empty();
            }

            byte[] bytes;
            try (InputStream body = response.body()) {
                bytes = readBounded(body, config.getMaxBytes());
            }
            if (bytes == null) {
                log.debug("Cover ambience: {} is larger than the {} byte budget",
                        coverUrl, config.getMaxBytes());
                return Optional.empty();
            }

            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                // Not an image, or a format this JVM has no reader for (webp).
                log.debug("Cover ambience: {} could not be decoded", coverUrl);
                return Optional.empty();
            }
            return CoverAmbience.from(image);
        } catch (IOException | RuntimeException e) {
            log.debug("Cover ambience: {} could not be read ({})", coverUrl, e.toString());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** @return the body, or null when it runs past {@code max} bytes */
    private static byte[] readBounded(InputStream in, int max) throws IOException {
        byte[] buffer = in.readNBytes(max + 1);
        return buffer.length > max ? null : buffer;
    }
}
