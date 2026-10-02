package com.funix.swp490x.mrs.service;

import com.funix.swp490x.mrs.catalog.CatalogObjectStore;
import com.funix.swp490x.mrs.catalog.CatalogProperties;
import com.funix.swp490x.mrs.catalog.CatalogStoreException;
import com.funix.swp490x.mrs.catalog.SongJsonMapper;
import com.funix.swp490x.mrs.catalog.SongJsonMapper.TagRef;
import com.funix.swp490x.mrs.catalog.StagedSong;
import com.funix.swp490x.mrs.domain.AuditLog;
import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.PlaylistSongRepository;
import com.funix.swp490x.mrs.repository.SongRepository;
import com.funix.swp490x.mrs.repository.TagRepository;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
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

    private static final Logger log = LoggerFactory.getLogger(SongCatalogService.class);

    /** Spec 4.10 Zone D. */
    public static final int PAGE_SIZE = 20;

    /** Hard cap on the preview-bar JSON so a catalog-wide play cannot ship every row. */
    public static final int PLAY_QUEUE_CAP = 100;

    private static final Sort TITLE_THEN_ID =
            Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id"));

    /** Hibernate will not bind an empty IN list, so unused filters get a dummy. */
    private static final List<Long> UNUSED_IDS = List.of(-1L);
    private static final List<String> UNUSED_PROVIDERS = List.of("");

    private final SongRepository songRepository;
    private final TagRepository tagRepository;
    private final CatalogObjectStore catalogStore;
    private final CatalogProperties catalogProperties;
    private final SongJsonMapper mapper;
    private final AuditLogRepository auditLogRepository;
    private final PlaylistSongRepository playlistSongRepository;
    private final PlaylistService playlistService;
    private final SongCatalogService self;

    public SongCatalogService(SongRepository songRepository,
            TagRepository tagRepository,
            CatalogObjectStore catalogStore,
            CatalogProperties catalogProperties,
            SongJsonMapper mapper,
            AuditLogRepository auditLogRepository,
            PlaylistSongRepository playlistSongRepository,
            PlaylistService playlistService,
            @Lazy SongCatalogService self) {
        this.songRepository = songRepository;
        this.tagRepository = tagRepository;
        this.catalogStore = catalogStore;
        this.catalogProperties = catalogProperties;
        this.mapper = mapper;
        this.auditLogRepository = auditLogRepository;
        this.playlistSongRepository = playlistSongRepository;
        this.playlistService = playlistService;
        this.self = self == null ? this : self;
    }

    /**
     * Pages songs using OR within each vocabulary and AND across selected vocabularies.
     * Loads page ids before tags to avoid collection-fetch pagination in memory.
     */
    @Transactional(readOnly = true)
    public Page<Song> search(List<String> providers, List<Long> genreIds, List<Long> moodIds,
            List<Long> artistIds, List<Long> tagIds, String query, boolean untaggedOnly,
            int page) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, TITLE_THEN_ID);
        Page<Long> ids = matchingIds(providers, genreIds, moodIds, artistIds, tagIds, query,
                pageable, false, untaggedOnly);

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

    /**
     * Returns playable catalog matches in title/id order, capped at {@link #PLAY_QUEUE_CAP}.
     * The queue spans pages and excludes songs without audio.
     */
    @Transactional(readOnly = true)
    public List<PreviewTrack> playQueue(List<String> providers, List<Long> genreIds,
            List<Long> moodIds, List<Long> artistIds, List<Long> tagIds, String query,
            boolean untaggedOnly) {

        Page<Long> ids = matchingIds(providers, genreIds, moodIds, artistIds, tagIds, query,
                Pageable.unpaged(TITLE_THEN_ID), false, untaggedOnly);
        if (ids.isEmpty()) {
            return List.of();
        }

        Map<Long, Song> byId = new LinkedHashMap<>();
        for (Song song : songRepository.findAllById(ids.getContent())) {
            byId.put(song.getId(), song);
        }
        return ids.getContent().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .filter(song -> StringUtils.hasText(song.getAudioUrl()))
                .map(PreviewTrack::checked)
                .limit(PLAY_QUEUE_CAP)
                .toList();
    }

    /** Resolve one preview without fetching its tag collection. */
    @Transactional(readOnly = true)
    public Optional<PreviewTrack> preview(Long songId) {
        return songRepository.findById(songId)
                .filter(song -> StringUtils.hasText(song.getAudioUrl()))
                .map(PreviewTrack::from);
    }

    /**
     * P-02: songs that match <em>any</em> selected genre, mood, artist, or tag
     * (and/or title/artist {@code query}), ordered by how many of those chips
     * the row carries, then title. {@code topN} caps the ranked list before
     * paging. Songs / admin catalog keep AND-across via {@link #search}.
     */
    @Transactional(readOnly = true)
    public Page<Song> searchRecommended(List<Long> genreIds, List<Long> moodIds,
            List<Long> artistIds, List<Long> tagIds, String query, Integer topN, int page) {

        List<Song> limited = rankedSongs(genreIds, moodIds, artistIds, tagIds, query, topN);
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE);
        int from = Math.min((int) pageable.getOffset(), limited.size());
        int to = Math.min(from + PAGE_SIZE, limited.size());
        return new PageImpl<>(limited.subList(from, to), pageable, limited.size());
    }

    /**
     * Playable tracks for the Search preview bar, in recommendation order.
     */
    @Transactional(readOnly = true)
    public List<PreviewTrack> playQueueRecommended(List<Long> genreIds, List<Long> moodIds,
            List<Long> artistIds, List<Long> tagIds, String query, Integer topN) {

        return rankedSongs(genreIds, moodIds, artistIds, tagIds, query, topN).stream()
                .filter(song -> StringUtils.hasText(song.getAudioUrl()))
                .map(PreviewTrack::checked)
                .limit(PLAY_QUEUE_CAP)
                .toList();
    }

    /**
     * Ids of every song the Search filters match, in recommendation order and
     * already cut to Top-N — the whole result set, not one page of it.
     */
    @Transactional(readOnly = true)
    public List<Long> recommendedIds(List<Long> genreIds, List<Long> moodIds,
            List<Long> artistIds, List<Long> tagIds, String query, Integer topN) {

        return rankedSongs(genreIds, moodIds, artistIds, tagIds, query, topN).stream()
                .map(Song::getId)
                .toList();
    }

    private List<Song> rankedSongs(List<Long> genreIds, List<Long> moodIds,
            List<Long> artistIds, List<Long> tagIds, String query, Integer topN) {

        Page<Long> ids = matchingIds(null, genreIds, moodIds, artistIds, tagIds, query,
                Pageable.unpaged(TITLE_THEN_ID), true, false);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Song> byId = new LinkedHashMap<>();
        for (Song song : songRepository.findAllWithTags(ids.getContent())) {
            byId.put(song.getId(), song);
        }
        Set<Long> relevance = relevanceIds(genreIds, moodIds, artistIds, tagIds);
        List<Song> ranked = ids.getContent().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .sorted(relevanceOrder(relevance))
                .toList();
        int cap = positive(topN) ? Math.min(topN, ranked.size()) : ranked.size();
        return ranked.subList(0, cap);
    }

    private Page<Long> matchingIds(List<String> providers, List<Long> genreIds,
            List<Long> moodIds, List<Long> artistIds, List<Long> tagIds, String query,
            Pageable pageable, boolean matchAny, boolean untaggedOnly) {

        List<String> providerValues = nonBlank(providers);
        boolean providerEmpty = providerValues.isEmpty();
        List<String> providersBound = providerEmpty ? UNUSED_PROVIDERS : providerValues;
        String q = StringUtils.hasText(query) ? query.trim() : null;
        if (untaggedOnly) {
            return songRepository.searchUntaggedIds(providerEmpty, providersBound, q, pageable);
        }
        boolean genreEmpty = empty(genreIds);
        boolean moodEmpty = empty(moodIds);
        boolean artistEmpty = empty(artistIds);
        boolean tagEmpty = empty(tagIds);
        List<Long> genreBound = genreEmpty ? UNUSED_IDS : genreIds;
        List<Long> moodBound = moodEmpty ? UNUSED_IDS : moodIds;
        List<Long> artistBound = artistEmpty ? UNUSED_IDS : artistIds;
        List<Long> tagBound = tagEmpty ? UNUSED_IDS : tagIds;

        if (matchAny) {
            return songRepository.searchIdsMatchingAny(
                    providerEmpty, providersBound,
                    genreEmpty, genreBound,
                    moodEmpty, moodBound,
                    artistEmpty, artistBound,
                    tagEmpty, tagBound,
                    q, pageable);
        }
        return songRepository.searchIds(
                providerEmpty, providersBound,
                genreEmpty, genreBound,
                moodEmpty, moodBound,
                artistEmpty, artistBound,
                tagEmpty, tagBound,
                q, pageable);
    }

    private static Set<Long> relevanceIds(List<Long> genreIds, List<Long> moodIds,
            List<Long> artistIds, List<Long> tagIds) {
        Set<Long> ids = new LinkedHashSet<>();
        addAll(ids, genreIds);
        addAll(ids, moodIds);
        addAll(ids, artistIds);
        addAll(ids, tagIds);
        return ids;
    }

    private static void addAll(Set<Long> into, List<Long> values) {
        if (values == null) {
            return;
        }
        for (Long id : values) {
            if (id != null) {
                into.add(id);
            }
        }
    }

    private static Comparator<Song> relevanceOrder(Set<Long> relevance) {
        return Comparator
                .comparingInt((Song song) -> matchCount(song, relevance)).reversed()
                .thenComparing(Song::getTitle, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(Song::getId, Comparator.nullsLast(Long::compareTo));
    }

    private static int matchCount(Song song, Set<Long> relevance) {
        if (relevance.isEmpty() || song.getTags() == null) {
            return 0;
        }
        int count = 0;
        for (var tag : song.getTags()) {
            if (tag.getId() != null && relevance.contains(tag.getId())) {
                count++;
            }
        }
        return count;
    }

    private static boolean positive(Integer value) {
        return value != null && value > 0;
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
     * Updates admin-editable classification under the song version check (UC-29, BR-06).
     * Writes staged JSON first, preserves provider fields and records its ETag; stale saves offer refresh only.
     * @throws CatalogStoreException when staging cannot be written
     */
    @Transactional
    public void update(Long id, int expectedVersion, SongEdit edit, Long actorId) {
        Song song = songRepository.findByIdWithTags(id)
                .orElseThrow(() -> new SongNotFoundException(id));
        if (song.getVersion() != expectedVersion) {
            throw new StaleSongException(id);
        }

        boolean explicit = Boolean.TRUE.equals(edit.explicit());
        List<String> genres = splitCsv(edit.genres());
        List<String> moods = splitCsv(edit.moods());
        List<String> tags = splitCsv(edit.tags());
        mapper.requireAllowlisted(genres, moods);

        String before = classificationJson(song);

        writeStagedClassification(song, explicit, genres, moods, tags);

        song.setExplicit(explicit);
        replaceTags(song, mapper.tagRefs(genres, moods, tags, song.getArtist()));

        try {
            songRepository.save(song);
        } catch (OptimisticLockingFailureException e) {
            throw new StaleSongException(id);
        }
        audit(actorId, AuditLog.ACTION_SONG_EDIT, song.getId(),
                "{\"title\":%s,\"before\":%s,\"after\":%s}".formatted(
                        jsonString(song.getTitle()), before, classificationJson(song)));
    }

    /**
     * Writes staged classification before MySQL; a later sync repairs a failed database write.
     * @throws CatalogStoreException when the song cannot be staged
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
     * Deletes hosted media and staged JSON before removing the song and compacting playlists (UC-29 A1).
     * Vendor media is untouched; the database phase retries once on an optimistic-lock failure.
     * @throws CatalogStoreException when an object-store deletion fails
     */
    public void delete(Long id, Long actorId) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new SongNotFoundException(id));
        String title = song.getTitle();
        String artist = song.getArtist();
        deleteHostedMedia(song);
        if (StringUtils.hasText(song.getExternalSourceId())) {
            catalogStore.deleteJson(catalogStore.stagingKey(song.getExternalSourceId()));
        }
        try {
            self.deleteCatalogRow(id, actorId, title, artist);
        } catch (OptimisticLockingFailureException e) {
            self.deleteCatalogRow(id, actorId, title, artist);
        }
    }

    /** Detaches the song, compacts affected playlists and deletes its row in one database transaction. */
    @Transactional
    public void deleteCatalogRow(Long id, Long actorId, String title, String artist) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new SongNotFoundException(id));
        LinkedHashSet<Long> playlistIds = new LinkedHashSet<>(
                playlistSongRepository.findPlaylistIdsBySongId(id));
        songRepository.detachFromPlaylists(List.of(id));
        for (Long playlistId : playlistIds) {
            playlistService.compactAfterRemoval(playlistId, actorId);
        }
        songRepository.delete(song);
        audit(actorId, AuditLog.ACTION_SONG_DELETE, id,
                "{\"title\":%s,\"artist\":%s,\"playlists\":%s}".formatted(
                        jsonString(title), jsonString(artist), jsonIds(playlistIds)));
    }

    /** Deletes hosted-media keys found in either the song row or staged JSON before removing that JSON. */
    private void deleteHostedMedia(Song song) {
        Set<String> keys = new LinkedHashSet<>();
        log.debug("Deleting hosted media for song {} externalId={} audioPrefix={} artworkPrefix={} store={}",
                song.getId(), song.getExternalSourceId(), catalogProperties.getMedia().getAudioPrefix(),
                catalogProperties.getMedia().getArtworkPrefix(), catalogStore.getClass().getSimpleName());
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
            log.debug("Deleting hosted media key {} for song {}", key, song.getId());
            catalogStore.deleteBinary(key);
        }
        log.debug("Deleted {} hosted media objects for song {}", keys.size(), song.getId());
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

    private void audit(Long actorId, String action, Long entityId, String details) {
        if (actorId == null || entityId == null) {
            return;
        }
        auditLogRepository.save(new AuditLog(actorId, action, AuditLog.ENTITY_SONG, entityId, details));
    }

    private static String classificationJson(Song song) {
        return "{\"explicit\":%s,\"genres\":%s,\"moods\":%s,\"tags\":%s}".formatted(
                Boolean.TRUE.equals(song.getExplicit()),
                jsonArray(names(song, TagType.GENRE)),
                jsonArray(names(song, TagType.MOOD)),
                jsonArray(names(song, TagType.TAGS)));
    }

    private static List<String> names(Song song, TagType type) {
        return song.getTags().stream()
                .filter(tag -> tag.getType() == type)
                .map(Tag::getName)
                .sorted()
                .toList();
    }

    private static String jsonArray(List<String> values) {
        return values.stream()
                .map(SongCatalogService::jsonString)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String jsonIds(Iterable<Long> ids) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (Long id : ids) {
            if (!first) {
                json.append(',');
            }
            json.append(id);
            first = false;
        }
        return json.append(']').toString();
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Classification the P-06b edit modal may change. Licensed identity is not here. */
    public record SongEdit(
            Boolean explicit,
            String genres,
            String moods,
            String tags) {
    }

    /** Preview-bar fields only — no tags, no licensed extras. */
    public record PreviewTrack(
            Long id,
            String url,
            String title,
            String artist,
            String cover,
            String ambienceA,
            String ambienceB,
            Integer duration) {

        static PreviewTrack checked(Song song) {
            return new PreviewTrack(song.getId(), "/songs/" + song.getId() + "/play",
                    song.getTitle(), song.getArtist(), song.getCoverUrl(),
                    song.getAmbienceA(), song.getAmbienceB(), song.getDuration());
        }

        static PreviewTrack from(Song song) {
            return new PreviewTrack(
                    song.getId(),
                    song.getAudioUrl(),
                    song.getTitle(),
                    song.getArtist(),
                    song.getCoverUrl(),
                    song.getAmbienceA(),
                    song.getAmbienceB(),
                    song.getDuration());
        }
    }
}
