package com.funix.swp490x.mrs.service;

import com.funix.swp490x.mrs.domain.AuditLog;
import com.funix.swp490x.mrs.domain.Playlist;
import com.funix.swp490x.mrs.domain.PlaylistSong;
import com.funix.swp490x.mrs.domain.PlaylistStatus;
import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.PlaylistRepository;
import com.funix.swp490x.mrs.repository.PlaylistRepository.CoverTile;
import com.funix.swp490x.mrs.repository.PlaylistRepository.OwnerOption;
import com.funix.swp490x.mrs.repository.PlaylistRepository.PublishedCardRow;
import com.funix.swp490x.mrs.repository.PlaylistRepository.SummaryRow;
import com.funix.swp490x.mrs.repository.PlaylistRepository.TagCount;
import com.funix.swp490x.mrs.repository.PlaylistSongRepository;
import com.funix.swp490x.mrs.repository.SongRepository;
import com.funix.swp490x.mrs.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * P-03a / P-03b playlists (FT-06). Owns the ordered contents of a playlist and
 * the state machine around Draft and Published.
 *
 * <p>Every mutation goes through {@link #editable}, which enforces both halves
 * of the rule the UI only hints at: the caller must own the playlist or hold a
 * collaborator grant (BR-03), and a Published playlist is read-only until it is
 * unpublished (DC-08).
 */
@Service
public class PlaylistService {

    /** Spec 4.4 Zone D. */
    public static final int PAGE_SIZE = 20;

    /** Spec 4.6 Zone D. */
    public static final int WORKSPACE_PAGE_SIZE = 12;

    private final PlaylistRepository playlistRepository;
    private final PlaylistSongRepository playlistSongRepository;
    private final SongRepository songRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    public PlaylistService(PlaylistRepository playlistRepository,
            PlaylistSongRepository playlistSongRepository,
            SongRepository songRepository,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository) {
        this.playlistRepository = playlistRepository;
        this.playlistSongRepository = playlistSongRepository;
        this.songRepository = songRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * One page of the playlists a user owns or collaborates on, newest change
     * first.
     *
     * <p>Two queries by design, as in {@link SongCatalogService#search}: the
     * page of ids, then everything those rows display. Counting songs and
     * collaborators per row inside the paged query would need joins that drop
     * empty playlists.
     */
    @Transactional(readOnly = true)
    public Page<PlaylistSummary> search(Long userId, PlaylistStatus status, String query, int page) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE);

        Page<Long> ids = playlistRepository.searchVisibleIds(
                userId,
                status == null ? "" : status.name(),
                StringUtils.hasText(query) ? query.trim() : "",
                pageable);

        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, ids.getTotalElements());
        }

        // The IN query returns its own order, so put the rows back into the
        // order the page established.
        Map<Long, PlaylistSummary> byId = new LinkedHashMap<>();
        for (SummaryRow row : playlistRepository.findSummaries(ids.getContent())) {
            byId.put(row.getId(), toSummary(row, userId));
        }
        List<PlaylistSummary> ordered = ids.getContent().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();

        return new PageImpl<>(ordered, pageable, ids.getTotalElements());
    }

    /** Drafts the Add-to-playlist dialog may offer, newest change first. */
    @Transactional(readOnly = true)
    public List<PlaylistOption> editableDrafts(Long userId) {
        List<Long> ids = playlistRepository.findEditableDraftIds(userId);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, PlaylistOption> byId = new LinkedHashMap<>();
        for (SummaryRow row : playlistRepository.findSummaries(ids)) {
            byId.put(row.getId(),
                    new PlaylistOption(row.getId(), row.getName(), count(row.getSongCount())));
        }
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    /**
     * One page of the Shared Workspace. Newest publish first. Drafts never
     * appear here — publishing is what puts a playlist on this list.
     */
    @Transactional(readOnly = true)
    public Page<PublishedPlaylistCard> searchPublished(Long ownerId, String query, int page) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), WORKSPACE_PAGE_SIZE);
        Page<Long> ids = playlistRepository.searchPublishedIds(
                ownerId == null ? 0L : ownerId,
                StringUtils.hasText(query) ? query.trim() : "",
                pageable);
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, ids.getTotalElements());
        }

        Map<Long, PublishedPlaylistCard> byId = toCards(ids.getContent());
        List<PublishedPlaylistCard> ordered = ids.getContent().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
        return new PageImpl<>(ordered, pageable, ids.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<PublishedOwner> publishedOwners() {
        List<PublishedOwner> owners = new java.util.ArrayList<>();
        for (OwnerOption row : playlistRepository.findPublishedOwners()) {
            owners.add(new PublishedOwner(row.getId(), row.getName()));
        }
        return owners;
    }

    /**
     * A published playlist any signed-in viewer may open from the Shared
     * Workspace. A Draft (or a missing id) is indistinguishable from not found,
     * so a stranger cannot probe for unpublished work.
     */
    @Transactional(readOnly = true)
    public Playlist viewPublished(Long playlistId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new PlaylistNotFoundException(playlistId));
        if (!playlist.isPublished()) {
            throw new PlaylistNotFoundException(playlistId);
        }
        return playlist;
    }

    /** Published, or a Draft the caller already owns / collaborates on. */
    @Transactional(readOnly = true)
    public Playlist viewExportable(Long playlistId, Long userId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new PlaylistNotFoundException(playlistId));
        if (playlist.isPublished()) {
            return playlist;
        }
        return visible(playlistId, userId);
    }

    @Transactional(readOnly = true)
    public String ownerName(Long ownerId) {
        if (ownerId == null) {
            return "";
        }
        return userRepository.findById(ownerId)
                .map(User::getUsername)
                .orElse("");
    }

    @Transactional(readOnly = true)
    public List<PlaylistSong> publishedSongs(Long playlistId) {
        viewPublished(playlistId);
        return playlistSongRepository.findOrdered(playlistId);
    }

    @Transactional(readOnly = true)
    public Playlist view(Long playlistId, Long userId) {
        return visible(playlistId, userId);
    }

    /**
     * The playlist's songs in position order, with the tags the detail table
     * shows. Read-only and transactional so the collections are initialised
     * before the view renders.
     */
    @Transactional(readOnly = true)
    public List<PlaylistSong> songs(Long playlistId, Long userId) {
        visible(playlistId, userId);
        return playlistSongRepository.findOrdered(playlistId);
    }

    /** Total playing time in seconds, for the detail footer. */
    @Transactional(readOnly = true)
    public long totalDuration(Long playlistId) {
        return playlistSongRepository.totalDuration(playlistId);
    }

    @Transactional
    public Playlist create(Long userId, String name) {
        String clean = requireName(name);
        Playlist playlist = playlistRepository.save(new Playlist(clean, userId));
        audit(userId, AuditLog.ACTION_PLAYLIST_CREATE, playlist.getId(),
                "{\"name\":\"" + escape(clean) + "\"}");
        return playlist;
    }

    /** Draft only — a Published playlist is locked until unpublished (DC-08). */
    @Transactional
    public void rename(Long playlistId, String name, Long userId) {
        Playlist playlist = editable(playlistId, userId);
        String clean = requireName(name);
        playlist.setName(clean);
        playlist.touch(userId);
        playlistRepository.save(playlist);
        audit(userId, AuditLog.ACTION_PLAYLIST_RENAME, playlistId,
                "{\"name\":\"" + escape(clean) + "\"}");
    }

    /**
     * Create-and-add, the dialog's one-step path from a song row to a brand-new
     * playlist. One transaction, so a failed add cannot leave an empty
     * playlist behind.
     */
    @Transactional
    public Playlist createWithSong(Long userId, String name, Long songId) {
        return createWithSongs(userId, name, List.of(songId));
    }

    /**
     * New Draft plus every listed song, skipping duplicates so a multi-select
     * from Search still lands a playlist.
     */
    @Transactional
    public Playlist createWithSongs(Long userId, String name, List<Long> songIds) {
        Playlist playlist = create(userId, name);
        if (songIds != null) {
            for (Long songId : songIds) {
                if (songId == null) {
                    continue;
                }
                try {
                    addSong(playlist.getId(), songId, userId);
                } catch (DuplicatePlaylistSongException ignored) {
                    // Same song twice in the selection — the first insert stands.
                }
            }
        }
        return playlist;
    }

    /**
     * Appends a song at the end of the playlist.
     *
     * @throws DuplicatePlaylistSongException when the song is already present —
     *     {@code playlist_song} is keyed by (playlist, song), so a second copy
     *     has nowhere to go
     */
    @Transactional
    public void addSong(Long playlistId, Long songId, Long userId) {
        Playlist playlist = editable(playlistId, userId);
        if (playlistSongRepository.existsByIdPlaylistIdAndIdSongId(playlistId, songId)) {
            throw new DuplicatePlaylistSongException(playlistId, songId);
        }
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new SongNotFoundException(songId));

        int position = playlistSongRepository.findMaxPosition(playlistId) + 1;
        playlistSongRepository.save(new PlaylistSong(playlistId, song, position));

        playlist.touch(userId);
        playlistRepository.save(playlist);
        audit(userId, AuditLog.ACTION_PLAYLIST_SONG_ADD, playlistId,
                "{\"songId\":" + songId + ",\"position\":" + position + "}");
    }

    /** Removes a song and closes the gap so positions stay contiguous 1..N. */
    @Transactional
    public void removeSong(Long playlistId, Long songId, Long userId) {
        Playlist playlist = editable(playlistId, userId);
        PlaylistSong entry = playlistSongRepository
                .findByIdPlaylistIdAndIdSongId(playlistId, songId)
                .orElseThrow(() -> new SongNotFoundException(songId));
        int removed = entry.getPosition();

        playlistSongRepository.deleteSong(playlistId, songId);
        closeGapAfter(playlistId, removed);

        playlist.touch(userId);
        playlistRepository.save(playlist);
        audit(userId, AuditLog.ACTION_PLAYLIST_SONG_REMOVE, playlistId,
                "{\"songId\":" + songId + "}");
    }

    /**
     * Swaps a song with its neighbour. Silently does nothing at either end, so
     * a double-click on the first row is not an error.
     */
    @Transactional
    public void move(Long playlistId, Long songId, boolean up, Long userId) {
        Playlist playlist = editable(playlistId, userId);
        PlaylistSong entry = playlistSongRepository
                .findByIdPlaylistIdAndIdSongId(playlistId, songId)
                .orElseThrow(() -> new SongNotFoundException(songId));

        int from = entry.getPosition();
        int to = up ? from - 1 : from + 1;
        if (to < 1 || to > playlistSongRepository.findMaxPosition(playlistId)) {
            return;
        }

        // Park, then swap, then bring back — a direct swap would collide on
        // uq_playlistsong_position halfway through (see PARK_OFFSET).
        playlistSongRepository.moveOne(playlistId, from, PlaylistSongRepository.PARK_OFFSET);
        playlistSongRepository.moveOne(playlistId, to, from);
        playlistSongRepository.moveOne(playlistId, PlaylistSongRepository.PARK_OFFSET, to);

        playlist.touch(userId);
        playlistRepository.save(playlist);
    }

    /** Draft only — a Published playlist has to be unpublished first (DC-05). */
    @Transactional
    public void delete(Long playlistId, Long userId) {
        Playlist playlist = visible(playlistId, userId);
        if (playlist.isPublished()) {
            throw new InvalidPlaylistStateException("Playlist " + playlistId + " is published");
        }
        playlistSongRepository.deleteAllOf(playlistId);
        playlistRepository.delete(playlist);
        audit(userId, AuditLog.ACTION_PLAYLIST_DELETE, playlistId, null);
    }

    /** Needs at least one song (BR-05). */
    @Transactional
    public void publish(Long playlistId, Long userId) {
        Playlist playlist = editable(playlistId, userId);
        if (playlistSongRepository.countByIdPlaylistId(playlistId) == 0) {
            throw new InvalidPlaylistStateException("Playlist " + playlistId + " has no songs");
        }
        playlist.setStatus(PlaylistStatus.PUBLISHED);
        playlist.setPublishedAt(LocalDateTime.now());
        playlist.touch(userId);
        playlistRepository.save(playlist);
        audit(userId, AuditLog.ACTION_PLAYLIST_PUBLISH, playlistId, null);
    }

    @Transactional
    public void unpublish(Long playlistId, Long userId) {
        Playlist playlist = visible(playlistId, userId);
        playlist.setStatus(PlaylistStatus.DRAFT);
        playlist.setPublishedAt(null);
        playlist.touch(userId);
        playlistRepository.save(playlist);
        audit(userId, AuditLog.ACTION_PLAYLIST_UNPUBLISH, playlistId, null);
    }

    /** RFC 4180 CSV of the playlist contents, in playing order. */
    @Transactional(readOnly = true)
    public String exportCsv(Long playlistId, Long userId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new PlaylistNotFoundException(playlistId));
        if (!playlist.isPublished()) {
            visible(playlistId, userId);
        }
        StringBuilder csv = new StringBuilder("position,title,artist,duration_seconds,provider\r\n");
        for (PlaylistSong entry : playlistSongRepository.findOrdered(playlistId)) {
            Song song = entry.getSong();
            csv.append(entry.getPosition()).append(',')
                    .append(csvField(song.getTitle())).append(',')
                    .append(csvField(song.getArtist())).append(',')
                    .append(song.getDuration() == null ? "" : song.getDuration()).append(',')
                    .append(csvField(song.getSourceProvider())).append("\r\n");
        }
        return csv.toString();
    }

    /**
     * Moves every row past {@code removed} out to the parking range, then back
     * one place lower. Two collision-free statements rather than one that MySQL
     * could evaluate in an order that trips the unique key.
     */
    private void closeGapAfter(Long playlistId, int removed) {
        playlistSongRepository.shiftAfter(playlistId, removed, PlaylistSongRepository.PARK_OFFSET);
        playlistSongRepository.shiftAfter(playlistId, PlaylistSongRepository.PARK_OFFSET,
                -(PlaylistSongRepository.PARK_OFFSET + 1));
    }

    private Map<Long, PublishedPlaylistCard> toCards(List<Long> ids) {
        Map<Long, List<String>> covers = new LinkedHashMap<>();
        for (CoverTile tile : playlistRepository.findCoverTiles(ids)) {
            covers.computeIfAbsent(tile.getPlaylistId(), key -> new java.util.ArrayList<>())
                    .add(tile.getCoverUrl());
        }

        Map<Long, List<TagCount>> tagRows = new LinkedHashMap<>();
        for (TagCount row : playlistRepository.findTagCounts(ids)) {
            tagRows.computeIfAbsent(row.getPlaylistId(), key -> new java.util.ArrayList<>()).add(row);
        }

        Map<Long, PublishedPlaylistCard> byId = new LinkedHashMap<>();
        for (PublishedCardRow row : playlistRepository.findPublishedCards(ids)) {
            List<String> topTags = tagRows.getOrDefault(row.getId(), List.of()).stream()
                    .sorted((a, b) -> Long.compare(count(b.getUses()), count(a.getUses())))
                    .limit(3)
                    .map(TagCount::getName)
                    .toList();
            byId.put(row.getId(), new PublishedPlaylistCard(
                    row.getId(),
                    row.getName(),
                    row.getOwnerName(),
                    count(row.getSongCount()),
                    row.getPublishedAt(),
                    covers.getOrDefault(row.getId(), List.of()),
                    topTags));
        }
        return byId;
    }

    private Playlist visible(Long playlistId, Long userId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new PlaylistNotFoundException(playlistId));
        // Not-found rather than forbidden: whether a playlist exists is itself
        // something a stranger should not learn.
        if (playlistRepository.countVisibleTo(playlistId, userId) == 0) {
            throw new PlaylistNotFoundException(playlistId);
        }
        return playlist;
    }

    private Playlist editable(Long playlistId, Long userId) {
        Playlist playlist = visible(playlistId, userId);
        if (playlist.isPublished()) {
            throw new PlaylistLockedException(playlistId);
        }
        return playlist;
    }

    private static String requireName(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            throw new InvalidPlaylistStateException("A playlist needs a name");
        }
        return clean.length() > 200 ? clean.substring(0, 200) : clean;
    }

    private void audit(Long actorId, String action, Long playlistId, String details) {
        if (actorId == null) {
            return;
        }
        auditLogRepository.save(new AuditLog(actorId, action, AuditLog.ENTITY_PLAYLIST,
                playlistId, details));
    }

    private static PlaylistSummary toSummary(SummaryRow row, Long userId) {
        return new PlaylistSummary(
                row.getId(),
                row.getName(),
                PlaylistStatus.valueOf(row.getStatus()),
                count(row.getSongCount()),
                row.getVersion() == null ? 0 : row.getVersion(),
                row.getLastModifiedAt(),
                row.getLastModifiedByName(),
                count(row.getCollaboratorCount()),
                // Reachable but not owned means it arrived through a grant (BR-03).
                !Objects.equals(row.getOwnerId(), userId));
    }

    private static long count(Long value) {
        return value == null ? 0L : value;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String csvField(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
