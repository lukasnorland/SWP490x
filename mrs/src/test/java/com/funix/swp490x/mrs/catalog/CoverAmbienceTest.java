package com.funix.swp490x.mrs.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What a cover turns into behind the player. */
class CoverAmbienceTest {

    /**
     * The wash is the cover colour pulled down towards the page: 200/30/60
     * darkens to 0.45 of itself, plus the fixed lift that keeps it off black.
     */
    @Test
    void takesTheDominantColourDarkenedForABackground() {
        Optional<CoverAmbience> ambience = CoverAmbience.from(solid(new Color(200, 30, 60)));

        assertThat(ambience).isPresent();
        assertThat(ambience.get().a()).isEqualTo("rgba(98, 22, 39, 0.9)");
        assertThat(ambience.get().b()).isEqualTo("rgba(98, 22, 39, 0.75)");
    }

    /** Two colours in, two different washes out, so the wash has some depth. */
    @Test
    void picksASecondColourWhenTheCoverHasOne() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(200, 30, 60));
        graphics.fillRect(0, 0, 64, 32);
        graphics.setColor(new Color(30, 120, 190));
        graphics.fillRect(0, 32, 64, 32);
        graphics.dispose();

        Optional<CoverAmbience> ambience = CoverAmbience.from(image);

        assertThat(ambience).isPresent();
        assertThat(ambience.get().a()).isNotEqualTo(ambience.get().b());
    }

    /**
     * A grey cover has no colour worth ranking, but it still gets a wash: the
     * alternative is a song that looks like the player is broken.
     */
    @Test
    void fallsBackToTheAverageWhenNothingIsColourful() {
        Optional<CoverAmbience> ambience = CoverAmbience.from(solid(new Color(128, 128, 128)));

        assertThat(ambience).isPresent();
        assertThat(ambience.get().a()).isEqualTo("rgba(66, 66, 70, 0.9)");
    }

    @Test
    void hasNothingToSayAboutAnEmptyOrTransparentCover() {
        BufferedImage transparent = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

        assertThat(CoverAmbience.from(transparent)).isEmpty();
        assertThat(CoverAmbience.from(null)).isEmpty();
    }

    /** CSS drops rgba(..., 0,9), so the decimal separator must not follow locale. */
    @Test
    void writesColoursCssCanActuallyParse() {
        CoverAmbience ambience = CoverAmbience.from(solid(new Color(10, 140, 90))).orElseThrow();

        assertThat(ambience.a()).matches("rgba\\(\\d+, \\d+, \\d+, 0\\.9\\)");
        assertThat(ambience.b()).matches("rgba\\(\\d+, \\d+, \\d+, 0\\.75\\)");
    }

    private static BufferedImage solid(Color color) {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, 64, 64);
        graphics.dispose();
        return image;
    }
}
