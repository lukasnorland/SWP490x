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
     *
     * <p>This class never writes the object store. S3 is applied onto MySQL, and
     * a row whose {@code source_etag} has already moved (an ADMIN edit that put
     * JSON after this run listed) is left alone rather than rolled back.
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
                Boolean created = apply(mapped.values(), item.object().etag(),
                        item.listedSourceEtag(), tagCache);
                if (created == null) {
                    skipped.add(new SkippedRow(item.key(),
                            "left in place — a later catalog write already updated this row"));
                    continue;
                }
                if (created) {
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

    /**
     * After songs are in step with staged JSON, drop tag names no song still
     * carries so the catalog filters match the data.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int pruneUnusedTags() {
        return tagRepository.deleteUnused();
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

    /**
     * @return true when created, false when updated, null when a concurrent
     *     S3+MySQL write already moved {@code source_etag} past this fetch
     */
    private Boolean apply(SongValues values, String etag, String listedSourceEtag,
            Map<String, Tag> tagCache) {
        Optional<Song> existing = songRepository.findBySourceProviderAndExternalSourceId(
                values.sourceProvider(), values.externalSourceId());

        Song song = existing.orElseGet(Song::new);
        boolean isNew = existing.isEmpty();

        if (!isNew && hasNewerWrite(song.getSourceEtag(), etag, listedSourceEtag)) {
            return null;
        }

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
     * True when MySQL already records a different object than the one this
     * fetch carried — typically an ADMIN edit that put JSON after we listed.
     * Applying would walk the row back and leave S3 ahead until another run.
     */
    static boolean hasNewerWrite(String currentEtag, String applyingEtag,
            String listedSourceEtag) {
        if (currentEtag == null || currentEtag.equals(applyingEtag)) {
            return false;
        }
        return listedSourceEtag == null || !currentEtag.equals(listedSourceEtag);
    }

    /**
     * Get-or-create against the shared vocabulary. The cache spans the chunk
     * because a genre like "Traditional Country" recurs across most of it.
     */
    private Set<Tag> resolveTags(Set<TagRef> refs, Map<String, Tag> tagCache) {
        Set<Tag> tags = new LinkedHashSet<>();
        for (TagRef ref : refs) {
            Tag tag = tagCache.computeIfAbsent(ref.dedupeKey(), key -> {
                Tag resolved = tagRepository.findByTypeAndName(ref.type(), ref.name())
                        .orElseGet(() -> tagRepository.save(new Tag(ref.type(), ref.name())));
                // MySQL compares names case-insensitively, so "female vocals"
                // is reused for "Female Vocals". Write the canonical spelling
                // onto the row so the filter dropdowns match the staged JSON.
                if (!resolved.getName().equals(ref.name())) {
                    resolved.setName(ref.name());
                    resolved = tagRepository.save(resolved);
                }
                return resolved;
            });
            tags.add(tag);
        }
        return tags;
    }

    /** Outcome of one chunk. */
    public record ChunkResult(int added, int updated, List<SkippedRow> skipped) {
    }
}
