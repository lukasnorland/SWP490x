package com.funix.swp490x.mrs.service;

import static org.mockito.ArgumentMatchers.eq;
import java.util.List;
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
import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.PlaylistRepository;
import com.funix.swp490x.mrs.repository.PlaylistSongRepository;
import com.funix.swp490x.mrs.repository.SongRepository;
import com.funix.swp490x.mrs.repository.UserRepository;
import java.util.Map;
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
    void createRefusesANameThatIsAlreadyTaken() {
        given(playlistRepository.existsByName("Morning coffee")).willReturn(true);

        assertThatThrownBy(() -> service.create(1L, "Morning coffee"))
                .isInstanceOf(DuplicatePlaylistNameException.class);

        then(playlistRepository).should(never()).save(any());
    }

    @Test
    void renameRefusesANameThatIsAlreadyTaken() {
        visible(playlist(7L, PlaylistStatus.DRAFT));
        given(playlistRepository.existsByNameAndIdNot("Launch party", 7L)).willReturn(true);

        assertThatThrownBy(() -> service.rename(7L, "Launch party", 1L))
                .isInstanceOf(DuplicatePlaylistNameException.class);
    }

    @Test
    void duplicateCopiesAPublishedPlaylistIntoANewDraftOwnedByTheActor() {
        Playlist source = playlist(7L, PlaylistStatus.PUBLISHED);
        given(playlistRepository.findById(7L)).willReturn(Optional.of(source));
        Song first = song(42L);
        Song second = song(43L);
        given(playlistSongRepository.findOrdered(7L)).willReturn(List.of(
                new PlaylistSong(7L, first, 1),
                new PlaylistSong(7L, second, 2)));
        stubNewDraft(11L, 3L);
        given(playlistSongRepository.existsByIdPlaylistIdAndIdSongId(eq(11L), any()))
                .willReturn(false);
        given(songRepository.findById(42L)).willReturn(Optional.of(first));
        given(songRepository.findById(43L)).willReturn(Optional.of(second));
        given(playlistSongRepository.findMaxPosition(11L)).willReturn(0, 1);

        Playlist copy = service.duplicate(7L, "Morning coffee (copy)", 3L);

        assertThat(copy.getId()).isEqualTo(11L);
        assertThat(copy.getName()).isEqualTo("Morning coffee (copy)");
        assertThat(copy.getStatus()).isEqualTo(PlaylistStatus.DRAFT);
        assertThat(copy.getOwnerId()).isEqualTo(3L);
        assertThat(source.getStatus()).isEqualTo(PlaylistStatus.PUBLISHED);
        then(playlistRepository).should(never()).countVisibleTo(7L, 3L);

        ArgumentCaptor<PlaylistSong> songs = ArgumentCaptor.forClass(PlaylistSong.class);
        then(playlistSongRepository).should(org.mockito.Mockito.times(2)).save(songs.capture());
        assertThat(songs.getAllValues())
                .extracting(entry -> entry.getId().getSongId())
                .containsExactly(42L, 43L);
        assertThat(songs.getAllValues()).extracting(PlaylistSong::getPosition)
                .containsExactly(1, 2);
    }

    @Test
    void duplicateOfADraftTheCallerCannotSeeIsNotFound() {
        given(playlistRepository.findById(7L))
                .willReturn(Optional.of(playlist(7L, PlaylistStatus.DRAFT)));
        given(playlistRepository.countVisibleTo(7L, 99L)).willReturn(0L);

        assertThatThrownBy(() -> service.duplicate(7L, "A copy", 99L))
                .isInstanceOf(PlaylistNotFoundException.class);

        then(playlistRepository).should(never()).save(any());
    }

    @Test
    void duplicateRefusesANameThatIsAlreadyTaken() {
        given(playlistRepository.findById(7L))
                .willReturn(Optional.of(playlist(7L, PlaylistStatus.PUBLISHED)));
        given(playlistSongRepository.findOrdered(7L)).willReturn(List.of());
        given(playlistRepository.existsByName("Morning coffee")).willReturn(true);

        assertThatThrownBy(() -> service.duplicate(7L, "Morning coffee", 3L))
                .isInstanceOf(DuplicatePlaylistNameException.class);

        then(playlistRepository).should(never()).save(any());
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

    /** The copy {@link PlaylistService#create} persists, then {@code addSong} reloads. */
    private void stubNewDraft(Long copyId, Long ownerId) {
        given(playlistRepository.save(any(Playlist.class))).willAnswer(invocation -> {
            Playlist saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", copyId);
            }
            return saved;
        });
        given(playlistRepository.findById(copyId)).willAnswer(invocation -> {
            Playlist copy = new Playlist("Morning coffee (copy)", ownerId);
            ReflectionTestUtils.setField(copy, "id", copyId);
            return Optional.of(copy);
        });
        given(playlistRepository.countVisibleTo(copyId, ownerId)).willReturn(1L);
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

    @Test
    void transferringOwnershipMovesEveryPlaylistAndAuditsEach() {
        Playlist draft = new Playlist("Morning", 7L);
        Playlist published = new Playlist("Evening", 7L);
        ReflectionTestUtils.setField(draft, "id", 11L);
        ReflectionTestUtils.setField(published, "id", 12L);
        given(playlistRepository.findByOwnerId(7L)).willReturn(List.of(draft, published));

        int moved = service.transferOwnership(7L, 1L, 1L);

        assertThat(moved).isEqualTo(2);
        assertThat(draft.getOwnerId()).isEqualTo(1L);
        assertThat(published.getOwnerId()).isEqualTo(1L);
        then(playlistRepository).should().saveAll(List.of(draft, published));
        then(playlistRepository).should().deleteCollaboratorGrantsOf(7L);

        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        then(auditLogRepository).should(org.mockito.Mockito.times(2)).save(audit.capture());
        assertThat(audit.getAllValues())
                .allSatisfy(entry -> {
                    assertThat(entry.getAction()).isEqualTo(AuditLog.ACTION_PLAYLIST_TRANSFER);
                    assertThat(entry.getActorId()).isEqualTo(1L);
                    assertThat(entry.getDetails()).contains("\"from\":7").contains("\"to\":1");
                });
    }

    @Test
    void transferringNothingStillClearsCollaboratorGrants() {
        given(playlistRepository.findByOwnerId(7L)).willReturn(List.of());

        assertThat(service.transferOwnership(7L, 1L, 1L)).isZero();

        then(playlistRepository).should(org.mockito.Mockito.never()).saveAll(org.mockito.ArgumentMatchers.any());
        then(playlistRepository).should().deleteCollaboratorGrantsOf(7L);
    }

    @Test
    void searchAllListsAnyOwnerWithTheOwnerNamed() {
        PlaylistRepository.SummaryRow row = org.mockito.Mockito.mock(PlaylistRepository.SummaryRow.class);
        given(row.getId()).willReturn(7L);
        given(row.getName()).willReturn("Morning coffee");
        given(row.getStatus()).willReturn("DRAFT");
        given(row.getVersion()).willReturn(2);
        given(row.getOwnerId()).willReturn(5L);
        given(row.getOwnerName()).willReturn("Dana Designer");
        given(playlistRepository.searchAllIds(eq(0L), eq(""), eq(""), any()))
                .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(7L)));
        given(playlistRepository.findSummaries(List.of(7L))).willReturn(List.of(row));

        var page = service.searchAll(null, null, null, 0);

        assertThat(page.getContent()).hasSize(1);
        PlaylistSummary summary = page.getContent().get(0);
        assertThat(summary.ownerName()).isEqualTo("Dana Designer");
        assertThat(summary.sharedWithMe()).isFalse();
        assertThat(summary.version()).isEqualTo(2);
    }

    @Test
    void inspectFindsAnyPlaylistOrReportsNotFound() {
        Playlist someoneElses = new Playlist("Launch party", 9L);
        given(playlistRepository.findById(8L)).willReturn(java.util.Optional.of(someoneElses));
        given(playlistRepository.findById(99L)).willReturn(java.util.Optional.empty());

        assertThat(service.inspect(8L)).isSameAs(someoneElses);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.inspect(99L))
                .isInstanceOf(PlaylistNotFoundException.class);
    }

    @Test
    void grantAddsAnActiveContentDesigner() {
        given(playlistRepository.findById(7L))
                .willReturn(Optional.of(playlist(7L, PlaylistStatus.DRAFT)));
        given(userRepository.findById(15L)).willReturn(Optional.of(designer(15L)));
        given(playlistRepository.countCollaboratorGrant(7L, 15L)).willReturn(0L);

        service.grant(7L, 15L, 1L);

        then(playlistRepository).should().insertCollaboratorGrant(7L, 15L, 1L);
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        then(auditLogRepository).should().save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(AuditLog.ACTION_PLAYLIST_COLLABORATOR_ADD);
    }

    @Test
    void aCollaboratorCannotGrant() {
        given(playlistRepository.findById(7L))
                .willReturn(Optional.of(playlist(7L, PlaylistStatus.DRAFT)));
        given(userRepository.findById(15L)).willReturn(Optional.of(designer(15L)));
        given(playlistRepository.countVisibleTo(7L, 15L)).willReturn(1L);

        assertThatThrownBy(() -> service.grant(7L, 99L, 15L))
                .isInstanceOf(InvalidCollaboratorException.class);

        then(playlistRepository).should(never()).insertCollaboratorGrant(any(), any(), any());
    }

    @Test
    void grantRefusesACustomer() {
        given(playlistRepository.findById(7L))
                .willReturn(Optional.of(playlist(7L, PlaylistStatus.DRAFT)));
        User customer = designer(15L);
        customer.setRole(Role.CUSTOMER);
        given(userRepository.findById(15L)).willReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.grant(7L, 15L, 1L))
                .isInstanceOf(InvalidCollaboratorException.class);

        then(playlistRepository).should(never()).insertCollaboratorGrant(any(), any(), any());
    }

    @Test
    void grantRefusesADuplicate() {
        given(playlistRepository.findById(7L))
                .willReturn(Optional.of(playlist(7L, PlaylistStatus.DRAFT)));
        given(userRepository.findById(15L)).willReturn(Optional.of(designer(15L)));
        given(playlistRepository.countCollaboratorGrant(7L, 15L)).willReturn(1L);

        assertThatThrownBy(() -> service.grant(7L, 15L, 1L))
                .isInstanceOf(DuplicateCollaboratorException.class);

        then(playlistRepository).should(never()).insertCollaboratorGrant(any(), any(), any());
    }

    @Test
    void afterAGrantTheCollaboratorCanSeeAndEditTheDraft() {
        Playlist playlist = playlist(7L, PlaylistStatus.DRAFT);
        given(playlistRepository.findById(7L)).willReturn(Optional.of(playlist));
        given(playlistRepository.countVisibleTo(7L, 15L)).willReturn(1L);
        given(playlistSongRepository.existsByIdPlaylistIdAndIdSongId(7L, 42L)).willReturn(false);
        given(songRepository.findById(42L)).willReturn(Optional.of(song(42L)));
        given(playlistSongRepository.findMaxPosition(7L)).willReturn(0);

        assertThat(service.view(7L, 15L)).isSameAs(playlist);
        service.addSong(7L, 42L, 15L);

        then(playlistSongRepository).should().save(any(PlaylistSong.class));
    }

    @Test
    void deleteIsOwnerOnly() {
        Playlist playlist = playlist(7L, PlaylistStatus.DRAFT);
        given(playlistRepository.findById(7L)).willReturn(Optional.of(playlist));
        given(playlistRepository.countVisibleTo(7L, 15L)).willReturn(1L);

        assertThatThrownBy(() -> service.delete(7L, 15L))
                .isInstanceOf(InvalidCollaboratorException.class);

        then(playlistRepository).should(never()).delete(any());
    }

    @Test
    void publishIsOwnerOnly() {
        Playlist playlist = playlist(7L, PlaylistStatus.DRAFT);
        given(playlistRepository.findById(7L)).willReturn(Optional.of(playlist));
        given(playlistRepository.countVisibleTo(7L, 15L)).willReturn(1L);

        assertThatThrownBy(() -> service.publish(7L, 15L))
                .isInstanceOf(InvalidCollaboratorException.class);

        then(playlistRepository).should(never()).save(any());
    }

    @Test
    void unpublishIsOwnerOnly() {
        Playlist playlist = playlist(7L, PlaylistStatus.PUBLISHED);
        given(playlistRepository.findById(7L)).willReturn(Optional.of(playlist));
        given(playlistRepository.countVisibleTo(7L, 15L)).willReturn(1L);

        assertThatThrownBy(() -> service.unpublish(7L, 15L))
                .isInstanceOf(InvalidCollaboratorException.class);

        then(playlistRepository).should(never()).save(any());
    }

    @Test
    void transferOwnedPlaylistsSendsOneToACollaboratorAndOneToAdmin() {
        Playlist withCollab = new Playlist("Morning", 7L);
        Playlist alone = new Playlist("Evening", 7L);
        ReflectionTestUtils.setField(withCollab, "id", 11L);
        ReflectionTestUtils.setField(alone, "id", 12L);
        given(playlistRepository.findByOwnerId(7L)).willReturn(List.of(withCollab, alone));
        given(playlistRepository.countCollaboratorGrant(11L, 15L)).willReturn(1L);
        given(userRepository.findById(15L)).willReturn(Optional.of(designer(15L)));

        int moved = service.transferOwnedPlaylists(7L, Map.of(11L, 15L, 12L, 1L), 1L);

        assertThat(moved).isEqualTo(2);
        assertThat(withCollab.getOwnerId()).isEqualTo(15L);
        assertThat(alone.getOwnerId()).isEqualTo(1L);
        then(playlistRepository).should().deleteCollaboratorGrant(11L, 15L);
        then(playlistRepository).should().deleteCollaboratorGrant(12L, 1L);
        then(playlistRepository).should().saveAll(List.of(withCollab, alone));
        then(playlistRepository).should().deleteCollaboratorGrantsOf(7L);
    }

    @Test
    void transferOwnedPlaylistsRejectsAMissingSuccessor() {
        Playlist owned = new Playlist("Morning", 7L);
        ReflectionTestUtils.setField(owned, "id", 11L);
        given(playlistRepository.findByOwnerId(7L)).willReturn(List.of(owned));

        assertThatThrownBy(() -> service.transferOwnedPlaylists(7L, Map.of(), 1L))
                .isInstanceOf(PlaylistSuccessorRequiredException.class);

        then(playlistRepository).should(never()).saveAll(any());
        then(playlistRepository).should(never()).deleteCollaboratorGrantsOf(any());
    }

    @Test
    void transferOwnedPlaylistsRejectsAStranger() {
        Playlist owned = new Playlist("Morning", 7L);
        ReflectionTestUtils.setField(owned, "id", 11L);
        given(playlistRepository.findByOwnerId(7L)).willReturn(List.of(owned));
        given(playlistRepository.countCollaboratorGrant(11L, 99L)).willReturn(0L);

        assertThatThrownBy(() -> service.transferOwnedPlaylists(7L, Map.of(11L, 99L), 1L))
                .isInstanceOf(InvalidSuccessorException.class);

        then(playlistRepository).should(never()).saveAll(any());
    }

    private static User designer(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("Dana Designer");
        user.setEmail("dana@mrs.local");
        user.setRole(Role.CONTENT_DESIGNER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
