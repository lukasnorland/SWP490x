package com.funix.swp490x.mrs.service;

import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.repository.SongRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Reads the catalog for P-06b. */
@Service
public class SongCatalogService {

    /** Spec 4.10 Zone D. */
    public static final int PAGE_SIZE = 20;

    private final SongRepository songRepository;

    public SongCatalogService(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    /**
     * One page of songs with their tags loaded.
     *
     * <p>Two queries by design: the page of ids, then that page's rows with
     * tags. Fetching tags and paging in a single query would make Hibernate
     * apply the limit in memory. Transactional because
     * {@code spring.jpa.open-in-view=false} means the collections have to be
     * initialised before the view renders.
     */
    @Transactional(readOnly = true)
    public Page<Song> search(String provider, Long tagId, String query,
            boolean untagged, boolean noPreview, int page) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id")));

        Page<Long> ids = songRepository.searchIds(
                StringUtils.hasText(provider) ? provider : null,
                tagId,
                StringUtils.hasText(query) ? query.trim() : null,
                untagged,
                noPreview,
                pageable);

        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, ids.getTotalElements());
        }

        // The IN query returns its own order, so put the rows back into the
        // order the page established.
        Map<Long, Song> byId = new LinkedHashMap<>();
        for (Song song : songRepository.findAllWithTags(ids.getContent())) {
            byId.put(song.getId(), song);
        }
        List<Song> ordered = ids.getContent().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();

        return new PageImpl<>(ordered, pageable, ids.getTotalElements());
    }

    public List<String> providers() {
        return songRepository.findDistinctProviders();
    }

    public long total() {
        return songRepository.count();
    }

    /** Headline for DC-03: these songs are invisible to filtered search. */
    public long untaggedCount() {
        return songRepository.countUntagged();
    }
}
