package com.funix.swp490x.mrs.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CatalogPropertiesTest {

    private final CatalogProperties.Media media = new CatalogProperties().getMedia();

    @Test
    void hostedObjectKeyExtractsCompanyMediaFromThePublicCdn() {
        assertThat(media.hostedObjectKey(
                "https://d34ixswlpjs53y.cloudfront.net/song-data/audio/ncs/abc-123.mp3"))
                .contains("song-data/audio/ncs/abc-123.mp3");
        assertThat(media.hostedObjectKey(
                "https://d34ixswlpjs53y.cloudfront.net/song-data/artwork/ncs/abc-123.jpg?w=300"))
                .contains("song-data/artwork/ncs/abc-123.jpg");
    }

    @Test
    void hostedObjectKeyIgnoresVendorCdns() {
        assertThat(media.hostedObjectKey(
                "https://audiocdn.epidemicsound.com/lqmp3/track.mp3")).isEmpty();
        assertThat(media.hostedObjectKey(
                "https://cdn.epidemicsound.com/curation-assets/cover.jpg")).isEmpty();
    }

    @Test
    void hostedObjectKeyRejectsPathsThatAreNotHostedMedia() {
        assertThat(media.hostedObjectKey(
                "https://d34ixswlpjs53y.cloudfront.net/song-data/abc-123.json")).isEmpty();
        assertThat(media.hostedObjectKey(
                "https://d34ixswlpjs53y.cloudfront.net/song-data/audio/ncs/../other.mp3"))
                .isEmpty();
    }
}
