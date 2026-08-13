package com.funix.swp490x.mrs.catalog;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The two colours a cover contributes to the shell's background wash, as CSS
 * {@code rgba()} values ready to drop into the {@code --shell-ambience-*}
 * custom properties.
 *
 * <p>The sampling mirrors what the browser used to do in {@code color.js}, so
 * a song looks the same whether its colours came from the server or from the
 * canvas fallback: reduce the cover to 32x32, bucket the pixels by their top
 * four bits per channel, ignore anything near black, near white or grey, and
 * take the two commonest buckets darkened into something a page can sit on.
 */
public record CoverAmbience(String a, String b) {

    /** Sampling grid. Big enough to survive a busy cover, small enough to be free. */
    private static final int SIZE = 32;

    /** Below this the pixel is transparent enough to say nothing about the art. */
    private static final int MIN_ALPHA = 200;

    private static final int MIN_LIGHTNESS = 28;
    private static final int MAX_LIGHTNESS = 230;
    private static final double MIN_SATURATION = 0.12;

    public static Optional<CoverAmbience> from(BufferedImage source) {
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return Optional.empty();
        }

        int[] pixels = scaleToGrid(source);
        Map<Integer, Bucket> buckets = new HashMap<>();
        long totalR = 0;
        long totalG = 0;
        long totalB = 0;
        int opaque = 0;

        for (int argb : pixels) {
            int alpha = (argb >>> 24) & 0xFF;
            if (alpha < MIN_ALPHA) {
                continue;
            }
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;

            totalR += r;
            totalG += g;
            totalB += b;
            opaque++;

            int max = Math.max(r, Math.max(g, b));
            int min = Math.min(r, Math.min(g, b));
            double lightness = (max + min) / 2.0;
            double saturation = max == 0 ? 0 : (max - min) / (double) max;
            if (lightness < MIN_LIGHTNESS || lightness > MAX_LIGHTNESS || saturation < MIN_SATURATION) {
                continue;
            }

            int key = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
            buckets.computeIfAbsent(key, k -> new Bucket()).add(r, g, b);
        }

        List<Bucket> ranked = new ArrayList<>(buckets.values());
        ranked.sort(Comparator.comparingInt((Bucket bucket) -> bucket.count).reversed()
                .thenComparingDouble(Bucket::red));

        if (ranked.isEmpty()) {
            // Every pixel was filtered out, so fall back to the whole-image
            // average rather than leaving a colourful cover with no wash.
            if (opaque == 0) {
                return Optional.empty();
            }
            Bucket average = new Bucket();
            average.count = opaque;
            average.r = totalR;
            average.g = totalG;
            average.b = totalB;
            ranked = List.of(average);
        }

        Bucket primary = ranked.get(0);
        Bucket secondary = ranked.size() > 1 ? ranked.get(1) : primary;
        return Optional.of(new CoverAmbience(
                wash(primary, "0.9"),
                wash(secondary, "0.75")));
    }

    /**
     * Redraws the cover into a fixed grid. Sampling every pixel of a 3000x3000
     * cover would cost far more than it tells us.
     */
    private static int[] scaleToGrid(BufferedImage source) {
        BufferedImage grid = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = grid.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, SIZE, SIZE, null);
        } finally {
            graphics.dispose();
        }
        return grid.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE);
    }

    /**
     * Darkens towards the page background. The raw cover colour is far too
     * bright to sit behind text, so it is pulled down and nudged blue.
     */
    private static String wash(Bucket bucket, String alpha) {
        long r = Math.round(bucket.red() * 0.45 + 8);
        long g = Math.round(bucket.green() * 0.45 + 8);
        long b = Math.round(bucket.blue() * 0.45 + 12);
        // Locale.ROOT because a locale with non-ASCII digits would produce a
        // colour the browser silently drops.
        return String.format(Locale.ROOT, "rgba(%d, %d, %d, %s)", r, g, b, alpha);
    }

    /** Running total for one colour bucket. */
    private static final class Bucket {

        private int count;
        private long r;
        private long g;
        private long b;

        void add(int red, int green, int blue) {
            count++;
            r += red;
            g += green;
            b += blue;
        }

        double red() {
            return r / (double) count;
        }

        double green() {
            return g / (double) count;
        }

        double blue() {
            return b / (double) count;
        }

    }
}
