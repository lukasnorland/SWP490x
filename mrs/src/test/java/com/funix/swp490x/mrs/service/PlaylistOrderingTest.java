package com.funix.swp490x.mrs.service;

import static org.assertj.core.api.Assertions.assertThat;

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
        songIds.forEach(songId -> playlistService.addSong(id, songId, ownerId));

        assertThat(positions(id)).containsExactly(1, 2, 3, 4);
        assertThat(titles(id)).containsExactly("First", "Second", "Third", "Fourth");

        playlistService.move(id, songIds.get(2), true, ownerId);
        assertThat(positions(id)).containsExactly(1, 2, 3, 4);
        assertThat(titles(id)).containsExactly("First", "Third", "Second", "Fourth");

        playlistService.move(id, songIds.get(0), false, ownerId);
        assertThat(titles(id)).containsExactly("Third", "First", "Second", "Fourth");

        // The gap has to close, or the next append reuses a taken position.
        playlistService.removeSong(id, songIds.get(0), ownerId);
        assertThat(positions(id)).containsExactly(1, 2, 3);
        assertThat(titles(id)).containsExactly("Third", "Second", "Fourth");

        playlistService.addSong(id, songIds.get(0), ownerId);
        assertThat(positions(id)).containsExactly(1, 2, 3, 4);
        assertThat(titles(id)).containsExactly("Third", "Second", "Fourth", "First");
    }

    /** The list and dialog queries are native, so nothing parses them at boot. */
    @Test
    void theNativeVisibilityQueriesReturnTheOwnersPlaylists() {
        Playlist playlist = playlistService.create(ownerId, "Ordering check");
        playlistService.addSong(playlist.getId(), songIds.get(0), ownerId);
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
        playlistService.addSong(playlist.getId(), songIds.get(0), ownerId);

        playlistService.publish(playlist.getId(), ownerId);
        assertThat(playlistService.view(playlist.getId(), ownerId).isPublished()).isTrue();
        entityManager.flush();
        assertThat(playlistService.searchPublished(null, null, 0).getContent())
                .extracting(PublishedPlaylistCard::name)
                .contains("Ordering check");

        playlistService.unpublish(playlist.getId(), ownerId);
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
