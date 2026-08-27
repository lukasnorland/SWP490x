package com.funix.swp490x.mrs.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** One card on P-04a Shared Workspace. */
public record PublishedPlaylistCard(
        Long id,
        String name,
        String ownerName,
        long songCount,
        LocalDateTime publishedAt,
        List<String> coverUrls,
        List<String> tags) {

    /** Always four slots, so the mosaic is a stable 2x2. */
    public List<String> mosaic() {
        List<String> tiles = new ArrayList<>(4);
        if (coverUrls != null) {
            for (String url : coverUrls) {
                if (url != null && !url.isBlank() && tiles.size() < 4) {
                    tiles.add(url);
                }
            }
        }
        while (tiles.size() < 4) {
            tiles.add(null);
        }
        return tiles;
    }
}
