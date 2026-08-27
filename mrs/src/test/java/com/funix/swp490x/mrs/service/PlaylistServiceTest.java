package com.funix.swp490x.mrs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import com.funix.swp490x.mrs.domain.AuditLog;
import com.funix.swp490x.mrs.domain.Playlist;
import com.funix.swp490x.mrs.domain.PlaylistSong;
import com.funix.swp490x.mrs.domain.PlaylistStatus;
import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.PlaylistRepository;
import com.funix.swp490x.mrs.repository.PlaylistSongRepository;
import com.funix.swp490x.mrs.repository.SongRepository;
import com.funix.swp490x.mrs.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    private static final int PARK = PlaylistSongRepository.PARK_OFFSET;

    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private PlaylistSongRepository playlistSongRepository;
    @Mock
    private SongRepository songRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogRepository auditLogRepository;

    private PlaylistService service;

    @BeforeEach
    void setUp() {
        service = new PlaylistService(playlistRepository, playlistSongRepository, songRepository,
                userRepository, auditLogRepository);
    }

    @Test
    void addSongAppendsAfterTheLastPosition() {
        visible(playlist(7L, PlaylistStatus.DRAFT));
        given(playlistSongRepository.existsByIdPlaylistIdAndIdSongId(7L, 42L)).willReturn(false);
        given(songRepository.findById(42L)).willReturn(Optional.of(song(42L)));
        given(playlistSongRepository.findMaxPosition(7L)).willReturn(3);

        service.addSong(7L, 42L, 1L);

        ArgumentCaptor<PlaylistSong> saved = ArgumentCaptor.forClass(PlaylistSong.class);
        then(playlistSongRepository).should().save(saved.capture());
        assertThat(saved.getValue().getPosition()).isEqualTo(4);
        assertThat(saved.getValue().getId().getPlaylistId()).isEqualTo(7L);
        assertThat(saved.getValue().getId().getSongId()).isEqualTo(42L);
    }

    /** An empty playlist starts at 1, which ck_plsong_position requires. */
    @Test
    void theFirstSongLandsAtPositionOne() {
        visible(playlist(7L, PlaylistStatus.DRAFT));
        given(playlistSongRepository.existsByIdPlaylistIdAndIdSongId(7L, 42L)).willReturn(false);
        given(songRepository.findById(42L)).willReturn(Optional.of(song(42L)));
        given(playlistSongRepository.findMaxPosition(7L)).willReturn(0);

        service.addSong(7L, 42L, 1L);

        ArgumentCaptor<PlaylistSong> saved = ArgumentCaptor.forClass(PlaylistSong.class);
        then(playlistSongRepository).should().save(saved.capture());
        assertThat(saved.getValue().getPosition()).isEqualTo(1);
    }

    @Test
    void addSongRefusesASongThePlaylistAlreadyHas() {
        visible(playlist(7L, PlaylistStatus.DRAFT));
        given(playlistSongRepository.existsByIdPlaylistIdAndIdSongId(7L, 42L)).willReturn(true);

        assertThatThrownBy(() -> service.addSong(7L, 42L, 1L))
                .isInstanceOf(DuplicatePlaylistSongException.class);

        then(playlistSongRepository).should(never()).save(any());
    }

    /** DC-08: publishing locks the contents until it is unpublished. */
    @Test
    void addSongRefusesAPublishedPlaylist() {
        visible(playlist(7L, PlaylistStatus.PUBLISHED));

        assertThatThrownBy(() -> service.addSong(7L, 42L, 1L))
                .isInstanceOf(PlaylistLockedException.class);

        then(playlistSongRepository).should(never()).save(any());
    }

    @Test
    void aPlaylistNotSharedWithTheCallerIsNotFound() {
        given(playlistRepository.findById(7L))
                .willReturn(Optional.of(playlist(7L, PlaylistStatus.DRAFT)));
        given(playlistRepository.countVisibleTo(7L, 99L)).willReturn(0L);

        assertThatThrownBy(() -> service.addSong(7L, 42L, 99L))
                .isInstanceOf(PlaylistNotFoundException.class);
    }

    /**
     * The two shifts are what keep uq_playlistsong_position satisfied at every
     * step: everything past the hole goes out to the parking range first, then
     * comes back one place lower.
     */
    @Test
    void removingASongRenumbersTheTailWithoutCollidingOnPosition() {
        visible(playlist(7L, PlaylistStatus.DRAFT));
        given(playlistSongRepository.findByIdPlaylistIdAndIdSongId(7L, 42L))
                .willReturn(Optional.of(new PlaylistSong(7L, song(42L), 2)));

        service.removeSong(7L, 42L, 1L);

        InOrder order = inOrder(playlistSongRepository);
        order.verify(playlistSongRepository).deleteSong(7L, 42L);
        order.verify(playlistSongRepository).shiftAfter(7L, 2, PARK);
        order.verify(playlistSongRepository).shiftAfter(7L, PARK, -(PARK + 1));
    }

    @Test
    void movingUpParksTheRowBeforeSwappingIt() {
        visible(playlist(7L, PlaylistStatus.DRAFT));
        given(playlistSongRepository.findByIdPlaylistIdAndIdSongId(7L, 42L))
                .willReturn(Optional.of(new PlaylistSong(7L, song(42L), 3)));
        given(playlistSongRepository.findMaxPosition(7L)).willReturn(4);

        service.move(7L, 42L, true, 1L);

        InOrder order = inOrder(playlistSongRepository);
        order.verify(playlistSongRepository).moveOne(7L, 3, PARK);
        order.verify(playlistSongRepository).moveOne(7L, 2, 3);
        order.verify(playlistSongRepository).moveOne(7L, PARK, 2);
    }

    /** A double-click on the first row is a no-op, not an error. */
    @Test
    void movingTheFirstSongUpChangesNothing() {
        visible(playlist(7L, PlaylistStatus.DRAFT));
        given(playlistSongRepository.findByIdPlaylistIdAndIdSongId(7L, 42L))
                .willReturn(Optional.of(new PlaylistSong(7L, song(42L), 1)));

        service.move(7L, 42L, true, 1L);

        then(playlistSongRepository).should(never()).moveOne(any(), any(Integer.class),
                any(Integer.class));
    }

    @Test
    void movingTheLastSongDownChangesNothing() {
        visible(playlist(7L, PlaylistStatus.DRAFT));
        given(playlistSongRepository.findByIdPlaylistIdAndIdSongId(7L, 42L))
                .willReturn(Optional.of(new PlaylistSong(7L, song(42L), 4)));
        given(playlistSongRepository.findMaxPosition(7L)).willReturn(4);

        service.move(7L, 42L, false, 1L);

        then(playlistSongRepository).should(never()).moveOne(any(), any(Integer.class),
                any(Integer.class));
    }

    /** BR-05: an empty playlist has nothing to publish. */
    @Test
    void publishNeedsAtLeastOneSong() {
        visible(playlist(7L, PlaylistStatus.DRAFT));
        given(playlistSongRepository.countByIdPlaylistId(7L)).willReturn(0L);

        assertThatThrownBy(() -> service.publish(7L, 1L))
                .isInstanceOf(InvalidPlaylistStateException.class);
    }

    /** DC-05: delete is Draft-only. */
    @Test
    void deleteRefusesAPublishedPlaylist() {
        visible(playlist(7L, PlaylistStatus.PUBLISHED));

        assertThatThrownBy(() -> service.delete(7L, 1L))
                .isInstanceOf(InvalidPlaylistStateException.class);

        then(playlistRepository).should(never()).delete(any());
    }

    @Test
    void createRefusesABlankName() {
        assertThatThrownBy(() -> service.create(1L, "   "))
                .isInstanceOf(InvalidPlaylistStateException.class);

        then(playlistRepository).should(never()).save(any());
    }

    @Test
    void createTrimsTheNameAndRecordsWhoDidIt() {
        given(playlistRepository.save(any(Playlist.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Playlist created = service.create(1L, "  Morning coffee  ");

        assertThat(created.getName()).isEqualTo("Morning coffee");
        assertThat(created.getOwnerId()).isEqualTo(1L);
        assertThat(created.getStatus()).isEqualTo(PlaylistStatus.DRAFT);

        ArgumentCaptor<AuditLog> logged = ArgumentCaptor.forClass(AuditLog.class);
        then(auditLogRepository).should().save(logged.capture());
        assertThat(logged.getValue().getAction()).isEqualTo(AuditLog.ACTION_PLAYLIST_CREATE);
    }

    @Test
    void renameUpdatesTheNameOnADraft() {
        Playlist playlist = playlist(7L, PlaylistStatus.DRAFT);
        visible(playlist);

        service.rename(7L, "  Evening tea  ", 1L);

        assertThat(playlist.getName()).isEqualTo("Evening tea");
        then(playlistRepository).should().save(playlist);
    }

    @Test
    void renameRefusesAPublishedPlaylist() {
        visible(playlist(7L, PlaylistStatus.PUBLISHED));

        assertThatThrownBy(() -> service.rename(7L, "Evening tea", 1L))
                .isInstanceOf(PlaylistLockedException.class);
    }

    @Test
    void renameRefusesABlankName() {
        visible(playlist(7L, PlaylistStatus.DRAFT));

        assertThatThrownBy(() -> service.rename(7L, "   ", 1L))
                .isInstanceOf(InvalidPlaylistStateException.class);
    }

    @Test
    void viewPublishedRefusesADraft() {
        given(playlistRepository.findById(7L))
                .willReturn(Optional.of(playlist(7L, PlaylistStatus.DRAFT)));

        assertThatThrownBy(() -> service.viewPublished(7L))
                .isInstanceOf(PlaylistNotFoundException.class);
    }

    @Test
    void exportQuotesEveryFieldSoACommaInATitleCannotSplitTheRow() {
        visible(playlist(7L, PlaylistStatus.DRAFT));
        Song song = song(42L);
        song.setTitle("Ice Cream, Again");
        song.setArtist("Sugar \"Blizz\"");
        song.setDuration(213);
        given(playlistSongRepository.findOrdered(7L))
                .willReturn(java.util.List.of(new PlaylistSong(7L, song, 1)));

        String csv = service.exportCsv(7L, 1L);

        assertThat(csv).contains("1,\"Ice Cream, Again\",\"Sugar \"\"Blizz\"\"\",213");
    }

    private void visible(Playlist playlist) {
        given(playlistRepository.findById(playlist.getId())).willReturn(Optional.of(playlist));
        given(playlistRepository.countVisibleTo(playlist.getId(), 1L)).willReturn(1L);
    }

    private static Playlist playlist(Long id, PlaylistStatus status) {
        Playlist playlist = new Playlist("Morning coffee", 1L);
        playlist.setStatus(status);
        playlist.touch(1L);
        ReflectionTestUtils.setField(playlist, "id", id);
        return playlist;
    }

    private static Song song(Long id) {
        Song song = new Song();
        song.setId(id);
        song.setTitle("Ice Cream");
        song.setArtist("Sugar Blizz");
        song.setSourceProvider("EpidemicSound");
        return song;
    }
}
