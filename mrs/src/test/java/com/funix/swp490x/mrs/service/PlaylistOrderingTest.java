package com.funix.swp490x.mrs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.funix.swp490x.mrs.domain.Playlist;
import com.funix.swp490x.mrs.domain.PlaylistSong;
import com.funix.swp490x.mrs.domain.PlaylistStatus;
import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.repository.PlaylistSongRepository;
import com.funix.swp490x.mrs.repository.SongRepository;
import com.funix.swp490x.mrs.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * The renumbering in {@link PlaylistService} against a real MySQL, because
 * {@code uq_playlistsong_position} is what it is written to survive and a
 * mocked repository cannot reject anything.
 *
 * <p>Runs inside the test transaction, so every row written here is rolled
 * back.
 */
@SpringBootTest
@Transactional
class PlaylistOrderingTest {

    @Autowired
    private PlaylistService playlistService;
    @Autowired
    private PlaylistSongRepository playlistSongRepository;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;

    private Long ownerId;
    private List<Long> songIds;

    @BeforeEach
    void seed() {
        ownerId = userRepository.findByEmail("admin@mrs.local").orElseThrow().getId();
        songIds = List.of(song("First"), song("Second"), song("Third"), song("Fourth"));
    }

    @Test
    void songsAppendInOrderAndSurviveMovesAndRemovals() {
        Playlist playlist = playlistService.create(ownerId, "Ordering check");
        Long id = playlist.getId();
        songIds.forEach(songId -> playlistService.addSong(id, version(id), songId, ownerId));

        assertThat(positions(id)).containsExactly(1, 2, 3, 4);
        assertThat(titles(id)).containsExactly("First", "Second", "Third", "Fourth");

        playlistService.move(id, version(id), songIds.get(2), true, ownerId);
        assertThat(positions(id)).containsExactly(1, 2, 3, 4);
        assertThat(titles(id)).containsExactly("First", "Third", "Second", "Fourth");

        playlistService.move(id, version(id), songIds.get(0), false, ownerId);
        assertThat(titles(id)).containsExactly("Third", "First", "Second", "Fourth");

        // The gap has to close, or the next append reuses a taken position.
        playlistService.removeSong(id, version(id), songIds.get(0), ownerId);
        assertThat(positions(id)).containsExactly(1, 2, 3);
        assertThat(titles(id)).containsExactly("Third", "Second", "Fourth");

        playlistService.addSong(id, version(id), songIds.get(0), ownerId);
        assertThat(positions(id)).containsExactly(1, 2, 3, 4);
        assertThat(titles(id)).containsExactly("Third", "Second", "Fourth", "First");
    }

    /**
     * DC-11: a new playlist is v1, and each accepted change adds exactly one.
     * Hibernate would otherwise seed a primitive at 0, and the number is what
     * the conflict screen shows the person who lost the race.
     */
    @Test
    void aNewPlaylistStartsAtVersionOneAndCountsUpByOne() {
        Playlist playlist = playlistService.create(ownerId, "Ordering check");
        Long id = playlist.getId();

        assertThat(version(id)).isEqualTo(1);

        playlistService.addSong(id, 1, songIds.get(0), ownerId);
        assertThat(version(id)).isEqualTo(2);

        playlistService.rename(id, 2, "Ordering check renamed", ownerId);
        assertThat(version(id)).isEqualTo(3);
    }

    /**
     * BR-06 against a real database: the version has to actually move on every
     * accepted write, or the check would pass forever and the second writer
     * would silently win.
     */
    @Test
    void everyAcceptedChangeMovesTheVersionAndAStaleOneIsRefused() {
        Playlist playlist = playlistService.create(ownerId, "Ordering check");
        Long id = playlist.getId();

        int afterCreate = version(id);
        playlistService.addSong(id, afterCreate, songIds.get(0), ownerId);
        int afterAdd = version(id);
        assertThat(afterAdd).isGreaterThan(afterCreate);

        playlistService.rename(id, afterAdd, "Ordering check renamed", ownerId);
        int afterRename = version(id);
        assertThat(afterRename).isGreaterThan(afterAdd);

        // Someone else already saved at afterRename, so this one is the loser.
        assertThatThrownBy(() ->
                playlistService.rename(id, afterAdd, "Too late", ownerId))
                .isInstanceOf(StalePlaylistException.class);
        assertThat(playlistService.view(id, ownerId).getName()).isEqualTo("Ordering check renamed");
    }

    /**
     * UC-19 A2: the rejected change lands on a copy the requester owns and the
     * playlist they collided with is untouched (POST-1, AC-04).
     */
    @Test
    void aCloneOnConflictCarriesTheChangeAndLeavesTheSourceAlone() {
        Playlist playlist = playlistService.create(ownerId, "Ordering check");
        Long id = playlist.getId();
        playlistService.addSong(id, version(id), songIds.get(0), ownerId);
        playlistService.addSong(id, version(id), songIds.get(1), ownerId);

        Playlist copy = playlistService.cloneOnConflict(id, "Ordering check copy", ownerId,
                PendingEdit.addSongs(List.of(songIds.get(2))));

        assertThat(titles(copy.getId())).containsExactly("First", "Second", "Third");
        assertThat(titles(id)).containsExactly("First", "Second");
        assertThat(copy.getOwnerId()).isEqualTo(ownerId);
        assertThat(copy.getStatus()).isEqualTo(PlaylistStatus.DRAFT);
    }

    private int version(Long playlistId) {
        entityManager.flush();
        entityManager.clear();
        return playlistService.view(playlistId, ownerId).getVersion();
    }

    /** The list and dialog queries are native, so nothing parses them at boot. */
    @Test
    void theNativeVisibilityQueriesReturnTheOwnersPlaylists() {
        Playlist playlist = playlistService.create(ownerId, "Ordering check");
        playlistService.addSong(playlist.getId(), version(playlist.getId()), songIds.get(0),
                ownerId);
        entityManager.flush();

        assertThat(playlistService.search(ownerId, null, null, 0).getContent())
                .extracting(PlaylistSummary::name)
                .contains("Ordering check");
        assertThat(playlistService.search(ownerId, PlaylistStatus.DRAFT, "orderING", 0).getContent())
                .extracting(PlaylistSummary::name)
                .contains("Ordering check");
        assertThat(playlistService.search(ownerId, PlaylistStatus.PUBLISHED, null, 0).getContent())
                .extracting(PlaylistSummary::name)
                .doesNotContain("Ordering check");
        assertThat(playlistService.editableDrafts(ownerId))
                .extracting(PlaylistOption::name)
                .contains("Ordering check");

        PlaylistSummary summary = playlistService.search(ownerId, null, "Ordering check", 0)
                .getContent().get(0);
        assertThat(summary.songCount()).isEqualTo(1);
        assertThat(summary.sharedWithMe()).isFalse();
        assertThat(summary.lastModifiedByName()).isEqualTo("System Admin");
    }

    @Test
    void publishThenUnpublishFlipsTheLock() {
        Playlist playlist = playlistService.create(ownerId, "Ordering check");
        Long id = playlist.getId();
        playlistService.addSong(id, version(id), songIds.get(0), ownerId);

        playlistService.publish(id, version(id), ownerId);
        assertThat(playlistService.view(playlist.getId(), ownerId).isPublished()).isTrue();
        entityManager.flush();
        assertThat(playlistService.searchPublished(null, null, 0).getContent())
                .extracting(PublishedPlaylistCard::name)
                .contains("Ordering check");

        playlistService.unpublish(id, version(id), ownerId);
        assertThat(playlistService.view(playlist.getId(), ownerId).isPublished()).isFalse();
        entityManager.flush();
        assertThat(playlistService.searchPublished(null, null, 0).getContent())
                .extracting(PublishedPlaylistCard::name)
                .doesNotContain("Ordering check");
    }

    private List<Integer> positions(Long playlistId) {
        entityManager.flush();
        entityManager.clear();
        return playlistSongRepository.findOrdered(playlistId).stream()
                .map(PlaylistSong::getPosition)
                .toList();
    }

    private List<String> titles(Long playlistId) {
        entityManager.flush();
        entityManager.clear();
        return playlistSongRepository.findOrdered(playlistId).stream()
                .map(entry -> entry.getSong().getTitle())
                .toList();
    }

    /**
     * A bare catalog row. Songs normally arrive only through the staged-JSON
     * import, but this needs ids to order and the transaction rolls back.
     */
    private Long song(String title) {
        Song song = new Song();
        song.setTitle(title);
        song.setArtist("Ordering Test");
        song.setSourceProvider("TestProvider");
        song.setExternalSourceId("ordering-it-" + title.toLowerCase());
        song.setDuration(120);
        return songRepository.save(song).getId();
    }
}
