package com.funix.swp490x.mrs.catalog;

import com.funix.swp490x.mrs.catalog.CatalogImportService.Fetched;
import com.funix.swp490x.mrs.catalog.ImportSummary.SkippedRow;
import com.funix.swp490x.mrs.catalog.SongJsonMapper.SongValues;
import com.funix.swp490x.mrs.catalog.SongJsonMapper.TagRef;
import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.repository.SongRepository;
import com.funix.swp490x.mrs.repository.TagRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one chunk of staged songs in a single transaction.
 *
 * <p>Separate from {@link CatalogImportService} so the transaction boundary is
 * a real bean call: a chunk either commits whole or leaves the previous chunks
 * intact, and the objects it did not manage to write still differ by ETag, so
 * the following sync retries exactly those.
 */
@Component
public class SongUpserter {

    private static final Logger log = LoggerFactory.getLogger(SongUpserter.class);

    private final SongRepository songRepository;
    private final TagRepository tagRepository;
    private final SongJsonMapper mapper;
    private final CatalogProperties properties;

    public SongUpserter(SongRepository songRepository,
            TagRepository tagRepository,
            SongJsonMapper mapper,
            CatalogProperties properties) {
        this.songRepository = songRepository;
        this.tagRepository = tagRepository;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChunkResult upsertChunk(List<Fetched> chunk) {
        List<SkippedRow> skipped = new ArrayList<>();
        Map<String, Tag> tagCache = new HashMap<>();
        int added = 0;
        int updated = 0;

        for (Fetched item : chunk) {
            SongJsonMapper.Result mapped = mapper.map(item.json(), properties.getProviders());
            if (mapped.isRejected()) {
                skipped.add(new SkippedRow(item.key(), mapped.rejection()));
                continue;
            }

            try {
                if (isDuplicateIsrc(mapped.values())) {
                    skipped.add(new SkippedRow(item.key(), "duplicate ISRC"));
                    continue;
                }
                if (apply(mapped.values(), item.object().etag(), tagCache)) {
                    added++;
                } else {
                    updated++;
                }
            } catch (OptimisticLockingFailureException e) {
                // Someone edited the song while the import was reading it. The
                // edit wins and the row keeps its old ETag, so the next sync
                // offers it again (BR-06).
                skipped.add(new SkippedRow(item.key(), "version conflict with a concurrent edit"));
            } catch (DataIntegrityViolationException e) {
                log.warn("Catalog import: {} violated a constraint", item.key(), e);
                skipped.add(new SkippedRow(item.key(), "rejected by the database"));
            }
        }

        return new ChunkResult(added, updated, skipped);
    }

    /**
     * Drops catalog rows whose staged object is gone. Playlist membership is
     * cleared first because {@code playlist_song} does not cascade.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int removeMissing(Collection<String> externalSourceIds) {
        if (externalSourceIds.isEmpty()) {
            return 0;
        }
        List<Long> ids = songRepository.findIdsByExternalSourceIdIn(externalSourceIds);
        if (!ids.isEmpty()) {
            songRepository.detachFromPlaylists(ids);
        }
        return songRepository.deleteByExternalSourceIdIn(externalSourceIds);
    }

    private boolean isDuplicateIsrc(SongValues values) {
        if (values.isrc() == null) {
            return false;
        }
        Optional<Song> byIsrc = songRepository.findByIsrc(values.isrc());
        if (byIsrc.isEmpty()) {
            return false;
        }
        Song owner = byIsrc.get();
        if (owner.getSourceProvider() == null || owner.getExternalSourceId() == null) {
            return true;
        }
        return !values.sourceProvider().equalsIgnoreCase(owner.getSourceProvider())
                || !values.externalSourceId().equals(owner.getExternalSourceId());
    }

    /** @return true when the song was created, false when updated in place */
    private boolean apply(SongValues values, String etag, Map<String, Tag> tagCache) {
        Optional<Song> existing = songRepository.findBySourceProviderAndExternalSourceId(
                values.sourceProvider(), values.externalSourceId());

        Song song = existing.orElseGet(Song::new);
        boolean isNew = existing.isEmpty();

        song.setTitle(values.title());
        song.setArtist(values.artist());
        song.setDuration(values.duration());
        song.setSourceProvider(values.sourceProvider());
        song.setExternalSourceId(values.externalSourceId());
        song.setBpm(values.bpm());
        song.setExplicit(values.explicit());
        song.setIsrc(values.isrc());
        song.setAudioUrl(values.audioUrl());
        song.setCoverUrl(values.coverUrl());
        // Committed with the row, so a hash never claims content that is not
        // actually stored.
        song.setSourceEtag(etag);
        song.setTags(resolveTags(values.tags(), tagCache));

        songRepository.save(song);
        return isNew;
    }

    /**
     * Get-or-create against the shared vocabulary. The cache spans the chunk
     * because a genre like "Traditional Country" recurs across most of it.
     */
    private Set<Tag> resolveTags(Set<TagRef> refs, Map<String, Tag> tagCache) {
        Set<Tag> tags = new LinkedHashSet<>();
        for (TagRef ref : refs) {
            Tag tag = tagCache.computeIfAbsent(ref.dedupeKey(), key ->
                    tagRepository.findByTypeAndName(ref.type(), ref.name())
                            .orElseGet(() -> tagRepository.save(new Tag(ref.type(), ref.name()))));
            tags.add(tag);
        }
        return tags;
    }

    /** Outcome of one chunk. */
    public record ChunkResult(int added, int updated, List<SkippedRow> skipped) {
    }
}
