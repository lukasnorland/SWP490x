package com.funix.swp490x.mrs.service;

import com.funix.swp490x.mrs.catalog.CatalogObjectStore;
import com.funix.swp490x.mrs.catalog.CatalogProperties;
import com.funix.swp490x.mrs.catalog.CatalogStoreException;
import com.funix.swp490x.mrs.catalog.SongJsonMapper;
import com.funix.swp490x.mrs.catalog.SongJsonMapper.TagRef;
import com.funix.swp490x.mrs.catalog.StagedSong;
import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.repository.SongRepository;
import com.funix.swp490x.mrs.repository.TagRepository;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Reads and edits the catalog for P-06b (UC-29). */
@Service
public class SongCatalogService {

    /** Spec 4.10 Zone D. */
    public static final int PAGE_SIZE = 20;

    /** Hibernate will not bind an empty IN list, so unused filters get a dummy. */
    private static final List<Long> UNUSED_IDS = List.of(-1L);
    private static final List<String> UNUSED_PROVIDERS = List.of("");

    private final SongRepository songRepository;
    private final TagRepository tagRepository;
    private final CatalogObjectStore catalogStore;
    private final CatalogProperties catalogProperties;
    private final SongJsonMapper mapper;

    public SongCatalogService(SongRepository songRepository,
            TagRepository tagRepository,
            CatalogObjectStore catalogStore,
            CatalogProperties catalogProperties,
            SongJsonMapper mapper) {
        this.songRepository = songRepository;
        this.tagRepository = tagRepository;
        this.catalogStore = catalogStore;
        this.catalogProperties = catalogProperties;
        this.mapper = mapper;
    }

    /**
     * One page of songs with their tags loaded.
     *
     * <p>Genre, mood and freeform tag filters are OR within a vocabulary and
     * AND across them: a song must match at least one selected value in each
     * category that has a selection. A null or empty list leaves that
     * vocabulary unconstrained.
     *
     * <p>Two queries by design: the page of ids, then that page's rows with
     * tags. Fetching tags and paging in a single query would make Hibernate
     * apply the limit in memory. Transactional because
     * {@code spring.jpa.open-in-view=false} means the collections have to be
     * initialised before the view renders.
     */
    @Transactional(readOnly = true)
    public Page<Song> search(List<String> providers, List<Long> genreIds, List<Long> moodIds,
            List<Long> tagIds, String query, int page) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id")));

        List<String> providerValues = nonBlank(providers);
        boolean providerEmpty = providerValues.isEmpty();
        boolean genreEmpty = empty(genreIds);
        boolean moodEmpty = empty(moodIds);
        boolean tagEmpty = empty(tagIds);

        Page<Long> ids = songRepository.searchIds(
                providerEmpty,
                providerEmpty ? UNUSED_PROVIDERS : providerValues,
                genreEmpty,
                genreEmpty ? UNUSED_IDS : genreIds,
                moodEmpty,
                moodEmpty ? UNUSED_IDS : moodIds,
                tagEmpty,
                tagEmpty ? UNUSED_IDS : tagIds,
                StringUtils.hasText(query) ? query.trim() : null,
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

    private static boolean empty(List<?> values) {
        return values == null || values.isEmpty();
    }

    private static List<String> nonBlank(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(StringUtils::hasText).toList();
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

    /**
     * Applies an ADMIN edit of classification only (explicit, genres, moods,
     * tags). Licensed identity — title, artist, duration, ISRC, media URLs —
     * is left untouched. The submitted version must still match (DC-02);
     * a mismatch is HTTP 409 with refresh only — songs have no clone (BR-06).
     *
     * <p>Writes the staged JSON first so the next import cannot overwrite the
     * edit. Unknown provider fields on an existing object are kept; a missing
     * object is created from the catalog row. {@code source_etag} is updated
     * to the written hash so an unchanged object is skipped on the next sync.
     * A song with no {@code externalSourceId} is refused — that would be a
     * MySQL-only write.
     *
     * @throws CatalogStoreException when the staged object could not be written
     */
    @Transactional
    public void update(Long id, int expectedVersion, SongEdit edit) {
        Song song = songRepository.findByIdWithTags(id)
                .orElseThrow(() -> new SongNotFoundException(id));
        if (song.getVersion() != expectedVersion) {
            throw new StaleSongException(id);
        }

        boolean explicit = Boolean.TRUE.equals(edit.explicit());
        List<String> genres = splitCsv(edit.genres());
        List<String> moods = splitCsv(edit.moods());
        List<String> tags = splitCsv(edit.tags());

        writeStagedClassification(song, explicit, genres, moods, tags);

        song.setExplicit(explicit);
        replaceTags(song, mapper.tagRefs(genres, moods, tags, song.getArtist()));

        try {
            songRepository.save(song);
        } catch (OptimisticLockingFailureException e) {
            throw new StaleSongException(id);
        }
    }

    /**
     * Patches (or creates) the staged object before the MySQL write. S3 is
     * outside the transaction: if MySQL then fails, the next import reapplies
     * the JSON. The other order would let a later import restore the old
     * classification over a successful MySQL edit.
     *
     * @throws CatalogStoreException when the song cannot be named in the store
     *     or the object could not be written
     */
    private void writeStagedClassification(Song song, boolean explicit, List<String> genres,
            List<String> moods, List<String> tags) {
        if (!StringUtils.hasText(song.getExternalSourceId())) {
            throw new CatalogStoreException(
                    "Song " + song.getId() + " has no externalSourceId; refusing a MySQL-only edit");
        }
        String key = catalogStore.stagingKey(song.getExternalSourceId());
        Optional<String> existing = catalogStore.findJson(key);
        String json;
        if (existing.isPresent()) {
            try {
                json = mapper.patchClassification(existing.get(), explicit, genres, moods, tags);
            } catch (RuntimeException e) {
                throw CatalogStoreException.of("Could not patch staged JSON " + key, e);
            }
        } else {
            json = mapper.write(new StagedSong(
                    song.getExternalSourceId(),
                    song.getSourceProvider(),
                    song.getTitle(),
                    song.getArtist(),
                    song.getDuration(),
                    song.getBpm(),
                    explicit,
                    song.getIsrc(),
                    song.getAudioUrl(),
                    song.getCoverUrl(),
                    mapper.cleanNames(genres),
                    mapper.cleanNames(moods),
                    mapper.cleanNames(tags)));
        }
        String etag = catalogStore.putJson(key, json);
        if (StringUtils.hasText(etag)) {
            song.setSourceEtag(etag);
        }
    }

    /**
     * Drops hosted media, then the staged JSON, then the catalog row, so
     * nothing of the song remains in the object store or MySQL. Vendor CDN
     * URLs are left alone — those bytes are not ours.
     *
     * @throws CatalogStoreException when a hosted object could not be removed
     */
    @Transactional
    public void delete(Long id) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new SongNotFoundException(id));
        deleteHostedMedia(song);
        if (StringUtils.hasText(song.getExternalSourceId())) {
            catalogStore.deleteJson(catalogStore.stagingKey(song.getExternalSourceId()));
        }
        songRepository.detachFromPlaylists(List.of(id));
        songRepository.delete(song);
    }

    /**
     * Audio/cover we host live under {@code song-data/audio|artwork/}. Collect
     * keys from the row and from the staged JSON (in case they differ) and
     * remove those objects before the JSON itself.
     */
    private void deleteHostedMedia(Song song) {
        Set<String> keys = new LinkedHashSet<>();
        addHostedKey(keys, song.getAudioUrl(), song.getExternalSourceId());
        addHostedKey(keys, song.getCoverUrl(), song.getExternalSourceId());
        if (StringUtils.hasText(song.getExternalSourceId())) {
            catalogStore.findJson(catalogStore.stagingKey(song.getExternalSourceId()))
                    .flatMap(mapper::readStaged)
                    .ifPresent(staged -> {
                        addHostedKey(keys, staged.audioUrl(), song.getExternalSourceId());
                        addHostedKey(keys, staged.coverUrl(), song.getExternalSourceId());
                    });
        }
        for (String key : keys) {
            catalogStore.deleteBinary(key);
        }
    }

    private void addHostedKey(Set<String> keys, String url, String externalSourceId) {
        catalogProperties.getMedia().hostedObjectKey(url).ifPresent(key -> {
            if (!StringUtils.hasText(externalSourceId)
                    || filenameStem(key).equals(externalSourceId)) {
                keys.add(key);
            }
        });
    }

    private static String filenameStem(String key) {
        int slash = key.lastIndexOf('/');
        String file = slash < 0 ? key : key.substring(slash + 1);
        int dot = file.lastIndexOf('.');
        return dot < 0 ? file : file.substring(0, dot);
    }

    private void replaceTags(Song song, Set<TagRef> refs) {
        Map<String, Tag> cache = new HashMap<>();
        Set<Tag> tags = new LinkedHashSet<>();
        for (TagRef ref : refs) {
            Tag tag = cache.computeIfAbsent(ref.dedupeKey(), key -> {
                Tag created = new Tag(ref.type(), ref.name());
                Tag resolved = tagRepository.findByTypeAndName(ref.type(), ref.name())
                        .orElseGet(() -> tagRepository.save(created));
                if (resolved == null) {
                    return created;
                }
                if (!resolved.getName().equals(ref.name())) {
                    resolved.setName(ref.name());
                    Tag saved = tagRepository.save(resolved);
                    return saved != null ? saved : resolved;
                }
                return resolved;
            });
            tags.add(tag);
        }
        song.getTags().clear();
        song.getTags().addAll(tags);
    }

    private static List<String> splitCsv(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,;]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Classification the P-06b edit modal may change. Licensed identity is not here. */
    public record SongEdit(
            Boolean explicit,
            String genres,
            String moods,
            String tags) {
    }
}
