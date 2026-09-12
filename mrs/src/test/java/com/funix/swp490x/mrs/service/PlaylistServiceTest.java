package com.funix.swp490x.mrs.service;

import static org.mockito.ArgumentMatchers.eq;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageImpl;
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

        service.addSong(7L, 1, 42L, 1L);

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

        service.addSong(7L, 1, 42L, 1L);

        ArgumentCaptor<PlaylistSong> saved = ArgumentCaptor.forClass(PlaylistSong.class);
        then(playlistSongRepository).should().save(saved.capture());
        assertThat(saved.getValue().getPosition()).isEqualTo(1);
    }

    @Test
    void addSongRefusesASongThePlaylistAlreadyHas() {
        visible(playlist(7L, PlaylistStatus.DRAFT));
        given(playlistSongRepository.existsByIdPlaylistIdAndIdSongId(7L, 42L)).willReturn(true);

        assertThatThrownBy(() -> service.addSong(7L, 1, 42L, 1L))
                .isInstanceOf(DuplicatePlaylistSongException.class);

        then(playlistSongRepository).should(never()).save(any());
    }

    /** DC-08: publishing locks the contents until it is unpublished. */
    @Test
    void addSongRefusesAPublishedPlaylist() {
        visible(playlist(7L, PlaylistStatus.PUBLISHED));

        assertThatThrownBy(() -> service.addSong(7L, 1, 42L, 1L))
                .isInstanceOf(PlaylistLockedException.class);

        then(playlistSongRepository).should(never()).save(any());
    }

    @Test
    void aPlaylistNotSharedWithTheCallerIsNotFound() {
        given(playlistRepository.findById(7L))
                .willReturn(Optional.of(playlist(7L, PlaylistStatus.DRAFT)));
        given(playlistRepository.countVisibleTo(7L, 99L)).willReturn(0L);

        assertThatThrownBy(() -> service.addSong(7L, 1, 42L, 99L))
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

        service.removeSong(7L, 1, 42L, 1L);

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

        service.move(7L, 1, 42L, true, 1L);

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

        service.move(7L, 1, 42L, true, 1L);

        then(playlistSongRepository).should(never()).moveOne(any(), any(Integer.class),
                any(Integer.class));
    }

    @Test
    void movingTheLastSongDownChangesNothing() {
        visible(playlist(7L, PlaylistStatus.DRAFT));
        given(playlistSongRepository.findByIdPlaylistIdAndIdSongId(7L, 42L))
                .willReturn(Optional.of(new PlaylistSong(7L, song(42L), 4)));
        given(playlistSongRepository.findMaxPosition(7L)).willReturn(4);

        service.move(7L, 1, 42L, false, 1L);

        then(playlistSongRepository).should(never()).moveOne(any(), any(Integer.class),
                any(Integer.class));
    }

    /** BR-05: an empty playlist has nothing to publish. */
    @Test
    void publishNeedsAtLeastOneSong() {
        given(playlistRepository.findById(7L))
                .willReturn(Optional.of(playlist(7L, PlaylistStatus.DRAFT)));
        given(playlistSongRepository.countByIdPlaylistId(7L)).willReturn(0L);

        assertThatThrownBy(() -> service.publish(7L, 1, 1L))
                .isInstanceOf(InvalidPlaylistStateException.class);
    }

    /** DC-05: delete is Draft-only. */
    @Test
    void deleteRefusesAPublishedPlaylist() {
        visible(playlist(7L, PlaylistStatus.PUBLISHED));

        assertThatThrownBy(() -> service.delete(7L, 1, 1L))
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

        service.rename(7L, 1, "  Evening tea  ", 1L);

        assertThat(playlist.getName()).isEqualTo("Evening tea");
        then(playlistRepository).should().save(playlist);
    }

    @Test
    void renameRefusesAPublishedPlaylist() {
        visible(playlist(7L, PlaylistStatus.PUBLISHED));

        assertThatThrownBy(() -> service.rename(7L, 1, "Evening tea", 1L))
                .isInstanceOf(PlaylistLockedException.class);
    }

    @Test
    void renameRefusesABlankName() {
        visible(playlist(7L, PlaylistStatus.DRAFT));

        assertThatThrownBy(() -> service.rename(7L, 1, "   ", 1L))
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

        assertThatThrownBy(() -> service.rename(7L, 1, "Launch party", 1L))
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
        stubNewDraft(11L);
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

    /**
     * The copy {@link PlaylistService#create} persists. Filling it needs no
     * further read: the songs go onto the entity already in hand, which is also
     * why a brand-new playlist has no version to check.
     */
    private void stubNewDraft(Long copyId) {
        given(playlistRepository.save(any(Playlist.class))).willAnswer(invocation -> {
            Playlist saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", copyId);
            }
            return saved;
        });
    }

    private static Playlist playlist(Long id, PlaylistStatus status) {
        Playlist playlist = new Playlist("Morning coffee", 1L);
        playlist.setStatus(status);
        playlist.touch(1L);
        ReflectionTestUtils.setField(playlist, "id", id);
        return playlist;
    }

    /** A Draft that has already been saved a few times, for the BR-06 checks. */
    private static Playlist atVersion(int version) {
        Playlist playlist = playlist(7L, PlaylistStatus.DRAFT);
        ReflectionTestUtils.setField(playlist, "version", version);
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

        service.grant(7L, 1, 15L, 1L);

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

        assertThatThrownBy(() -> service.grant(7L, 1, 99L, 15L))
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

        assertThatThrownBy(() -> service.grant(7L, 1, 15L, 1L))
                .isInstanceOf(InvalidCollaboratorException.class);

        then(playlistRepository).should(never()).insertCollaboratorGrant(any(), any(), any());
    }

    @Test
    void grantRefusesADuplicate() {
        given(playlistRepository.findById(7L))
                .willReturn(Optional.of(playlist(7L, PlaylistStatus.DRAFT)));
        given(userRepository.findById(15L)).willReturn(Optional.of(designer(15L)));
        given(playlistRepository.countCollaboratorGrant(7L, 15L)).willReturn(1L);

        assertThatThrownBy(() -> service.grant(7L, 1, 15L, 1L))
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
        service.addSong(7L, 1, 42L, 15L);

        then(playlistSongRepository).should().save(any(PlaylistSong.class));
    }

    @Test
    void deleteIsOwnerOnly() {
        Playlist playlist = playlist(7L, PlaylistStatus.DRAFT);
        given(playlistRepository.findById(7L)).willReturn(Optional.of(playlist));
        given(playlistRepository.countVisibleTo(7L, 15L)).willReturn(1L);

        assertThatThrownBy(() -> service.delete(7L, 1, 15L))
                .isInstanceOf(InvalidCollaboratorException.class);

        then(playlistRepository).should(never()).delete(any());
    }

    @Test
    void publishIsOwnerOnly() {
        Playlist playlist = playlist(7L, PlaylistStatus.DRAFT);
        given(playlistRepository.findById(7L)).willReturn(Optional.of(playlist));
        given(playlistRepository.countVisibleTo(7L, 15L)).willReturn(1L);

        assertThatThrownBy(() -> service.publish(7L, 1, 15L))
                .isInstanceOf(InvalidCollaboratorException.class);

        then(playlistRepository).should(never()).save(any());
    }

    @Test
    void unpublishIsOwnerOnly() {
        Playlist playlist = playlist(7L, PlaylistStatus.PUBLISHED);
        given(playlistRepository.findById(7L)).willReturn(Optional.of(playlist));
        given(playlistRepository.countVisibleTo(7L, 15L)).willReturn(1L);

        assertThatThrownBy(() -> service.unpublish(7L, 1, 15L))
                .isInstanceOf(InvalidCollaboratorException.class);

        then(playlistRepository).should(never()).save(any());
    }

    @Test
    void anAdminCanPublishAnotherOwnersDraft() {
        Playlist playlist = playlist(7L, PlaylistStatus.DRAFT);
        given(playlistRepository.findById(7L)).willReturn(Optional.of(playlist));
        given(userRepository.findById(99L)).willReturn(Optional.of(admin(99L)));
        given(playlistSongRepository.countByIdPlaylistId(7L)).willReturn(1L);

        service.publish(7L, 1, 99L);

        assertThat(playlist.getStatus()).isEqualTo(PlaylistStatus.PUBLISHED);
        then(playlistRepository).should().save(playlist);
    }

    @Test
    void anAdminCanUnpublishAnotherOwnersPlaylist() {
        Playlist playlist = playlist(7L, PlaylistStatus.PUBLISHED);
        given(playlistRepository.findById(7L)).willReturn(Optional.of(playlist));
        given(userRepository.findById(99L)).willReturn(Optional.of(admin(99L)));

        service.unpublish(7L, 1, 99L);

        assertThat(playlist.getStatus()).isEqualTo(PlaylistStatus.DRAFT);
        then(playlistRepository).should().save(playlist);
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

    @Test
    void search_whenOwner_shouldBindUserIdToVisibilityQuery() {
        PlaylistRepository.SummaryRow row = summary(7L, "Morning coffee", "DRAFT", 1L);
        given(playlistRepository.searchVisibleIds(eq(1L), eq(""), eq(""), any()))
                .willReturn(new PageImpl<>(List.of(7L)));
        given(playlistRepository.findSummaries(List.of(7L))).willReturn(List.of(row));

        var page = service.search(1L, null, null, 0);

        then(playlistRepository).should().searchVisibleIds(eq(1L), eq(""), eq(""), any());
        assertThat(page.getContent()).extracting(PlaylistSummary::id).containsExactly(7L);
        assertThat(page.getContent().get(0).sharedWithMe()).isFalse();
    }

    @Test
    void search_whenRepositoryReturnsSharedDraft_shouldMapIt() {
        PlaylistRepository.SummaryRow row = summary(7L, "Shared draft", "DRAFT", 5L);
        given(playlistRepository.searchVisibleIds(eq(1L), eq(""), eq(""), any()))
                .willReturn(new PageImpl<>(List.of(7L)));
        given(playlistRepository.findSummaries(List.of(7L))).willReturn(List.of(row));

        var page = service.search(1L, null, null, 0);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).sharedWithMe()).isTrue();
        assertThat(page.getContent().get(0).status()).isEqualTo(PlaylistStatus.DRAFT);
    }

    @Test
    void search_whenStatusFilterDraft_shouldExcludePublished() {
        PlaylistRepository.SummaryRow row = summary(7L, "Morning coffee", "DRAFT", 1L);
        given(playlistRepository.searchVisibleIds(eq(1L), eq("DRAFT"), eq(""), any()))
                .willReturn(new PageImpl<>(List.of(7L)));
        given(playlistRepository.findSummaries(List.of(7L))).willReturn(List.of(row));

        var page = service.search(1L, PlaylistStatus.DRAFT, null, 0);

        then(playlistRepository).should().searchVisibleIds(eq(1L), eq("DRAFT"), eq(""), any());
        assertThat(page.getContent()).extracting(PlaylistSummary::status)
                .containsOnly(PlaylistStatus.DRAFT);
    }

    @Test
    void searchPublished_shouldListEveryPublishedPlaylist() {
        PlaylistRepository.PublishedCardRow row = publishedCard(7L, "Launch");
        given(playlistRepository.searchPublishedIds(eq(0L), eq(""), any()))
                .willReturn(new PageImpl<>(List.of(7L)));
        given(playlistRepository.findPublishedCards(List.of(7L))).willReturn(List.of(row));

        var page = service.searchPublished(null, null, 0);

        assertThat(page.getContent()).extracting(PublishedPlaylistCard::id).containsExactly(7L);
        assertThat(page.getContent().get(0).name()).isEqualTo("Launch");
    }

    @Test
    void searchPublished_shouldExcludeDrafts() {
        given(playlistRepository.searchPublishedIds(eq(0L), eq(""), any()))
                .willReturn(new PageImpl<>(List.of()));

        var page = service.searchPublished(null, null, 0);

        then(playlistRepository).should().searchPublishedIds(eq(0L), eq(""), any());
        assertThat(page.getContent()).isEmpty();
        then(playlistRepository).should(never()).findPublishedCards(any());
    }

    @Test
    void view_whenOwner_shouldReturnPlaylist() {
        Playlist playlist = playlist(7L, PlaylistStatus.DRAFT);
        visible(playlist);

        assertThat(service.view(7L, 1L)).isSameAs(playlist);
    }

    @Test
    void view_whenCollaborator_shouldReturnPlaylist() {
        Playlist playlist = playlist(7L, PlaylistStatus.DRAFT);
        given(playlistRepository.findById(7L)).willReturn(Optional.of(playlist));
        given(playlistRepository.countVisibleTo(7L, 15L)).willReturn(1L);

        assertThat(service.view(7L, 15L)).isSameAs(playlist);
    }

    @Test
    void view_whenNotVisibleToCaller_shouldThrowNotFound() {
        given(playlistRepository.findById(7L))
                .willReturn(Optional.of(playlist(7L, PlaylistStatus.DRAFT)));
        given(playlistRepository.countVisibleTo(7L, 99L)).willReturn(0L);

        assertThatThrownBy(() -> service.view(7L, 99L))
                .isInstanceOf(PlaylistNotFoundException.class);
    }

    @Test
    void create_whenNameIsOneHundredChars_shouldAccept() {
        String name = "a".repeat(100);
        given(playlistRepository.save(any(Playlist.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Playlist created = service.create(1L, name);

        assertThat(created.getName()).isEqualTo(name);
        assertThat(created.getName()).hasSize(100);
    }

    @Test
    void create_whenNameExceedsTwoHundred_shouldTruncate() {
        given(playlistRepository.save(any(Playlist.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Playlist created = service.create(1L, "a".repeat(201));

        assertThat(created.getName()).hasSize(200);
    }

    @Test
    void create_shouldStartAsDraft() {
        given(playlistRepository.save(any(Playlist.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Playlist created = service.create(1L, "Morning coffee");

        assertThat(created.getStatus()).isEqualTo(PlaylistStatus.DRAFT);
        assertThat(created.getPublishedAt()).isNull();
    }

    @Test
    void createWithSongs_shouldNumberPositionsContiguously() {
        stubNewDraft(20L);
        given(playlistSongRepository.existsByIdPlaylistIdAndIdSongId(eq(20L), any()))
                .willReturn(false);
        given(songRepository.findById(1L)).willReturn(Optional.of(song(1L)));
        given(songRepository.findById(2L)).willReturn(Optional.of(song(2L)));
        given(songRepository.findById(3L)).willReturn(Optional.of(song(3L)));
        given(playlistSongRepository.findMaxPosition(20L)).willReturn(0, 1, 2);

        service.createWithSongs(1L, "Morning coffee", List.of(1L, 2L, 3L));

        ArgumentCaptor<PlaylistSong> saved = ArgumentCaptor.forClass(PlaylistSong.class);
        then(playlistSongRepository).should(org.mockito.Mockito.times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(PlaylistSong::getPosition)
                .containsExactly(1, 2, 3);
    }

    @Test
    void createWithSongs_whenListIsEmpty_shouldCreateEmptyDraft() {
        given(playlistRepository.save(any(Playlist.class))).willAnswer(invocation -> {
            Playlist saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 20L);
            return saved;
        });

        Playlist created = service.createWithSongs(1L, "Empty", List.of());

        assertThat(created.getStatus()).isEqualTo(PlaylistStatus.DRAFT);
        then(playlistSongRepository).should(never()).save(any());
    }

    @Test
    void createWithSongs_whenListHasDuplicate_shouldKeepFirstOccurrence() {
        stubNewDraft(20L);
        given(playlistSongRepository.existsByIdPlaylistIdAndIdSongId(20L, 42L))
                .willReturn(false, true);
        given(songRepository.findById(42L)).willReturn(Optional.of(song(42L)));
        given(playlistSongRepository.findMaxPosition(20L)).willReturn(0);

        service.createWithSongs(1L, "Morning coffee", List.of(42L, 42L));

        then(playlistSongRepository).should().save(any(PlaylistSong.class));
    }

    @Test
    void rename_whenNameIsOneHundredCharsAfterTrim_shouldAccept() {
        Playlist playlist = playlist(7L, PlaylistStatus.DRAFT);
        visible(playlist);
        String name = "a".repeat(100);

        service.rename(7L, 1, "  " + name + "  ", 1L);

        assertThat(playlist.getName()).isEqualTo(name);
    }

    @Test
    void rename_whenCollaborator_shouldSucceed() {
        Playlist playlist = playlist(7L, PlaylistStatus.DRAFT);
        given(playlistRepository.findById(7L)).willReturn(Optional.of(playlist));
        given(playlistRepository.countVisibleTo(7L, 15L)).willReturn(1L);

        service.rename(7L, 1, "Evening tea", 15L);

        assertThat(playlist.getName()).isEqualTo("Evening tea");
        then(playlistRepository).should().save(playlist);
    }

    @Test
    void removeSong_whenLastSong_shouldLeaveEmptyDraft() {
        Playlist playlist = playlist(7L, PlaylistStatus.DRAFT);
        visible(playlist);
        given(playlistSongRepository.findByIdPlaylistIdAndIdSongId(7L, 42L))
                .willReturn(Optional.of(new PlaylistSong(7L, song(42L), 1)));

        service.removeSong(7L, 1, 42L, 1L);

        then(playlistSongRepository).should().deleteSong(7L, 42L);
        assertThat(playlist.getStatus()).isEqualTo(PlaylistStatus.DRAFT);
    }

    @Test
    void removeSong_whenSongNotMember_shouldThrow() {
        visible(playlist(7L, PlaylistStatus.DRAFT));
        given(playlistSongRepository.findByIdPlaylistIdAndIdSongId(7L, 42L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeSong(7L, 1, 42L, 1L))
                .isInstanceOf(SongNotFoundException.class);
    }

    @Test
    void removeSong_whenPublished_shouldThrowLocked() {
        visible(playlist(7L, PlaylistStatus.PUBLISHED));

        assertThatThrownBy(() -> service.removeSong(7L, 1, 42L, 1L))
                .isInstanceOf(PlaylistLockedException.class);
    }

    @Test
    void move_whenCollaborator_shouldSwapPositions() {
        Playlist playlist = playlist(7L, PlaylistStatus.DRAFT);
        given(playlistRepository.findById(7L)).willReturn(Optional.of(playlist));
        given(playlistRepository.countVisibleTo(7L, 15L)).willReturn(1L);
        given(playlistSongRepository.findByIdPlaylistIdAndIdSongId(7L, 42L))
                .willReturn(Optional.of(new PlaylistSong(7L, song(42L), 1)));
        given(playlistSongRepository.findMaxPosition(7L)).willReturn(2);

        service.move(7L, 1, 42L, false, 15L);

        then(playlistSongRepository).should().moveOne(7L, 1, PARK);
        then(playlistSongRepository).should().moveOne(7L, 2, 1);
        then(playlistSongRepository).should().moveOne(7L, PARK, 2);
    }

    @Test
    void move_whenPublished_shouldThrowLocked() {
        visible(playlist(7L, PlaylistStatus.PUBLISHED));

        assertThatThrownBy(() -> service.move(7L, 1, 42L, false, 1L))
                .isInstanceOf(PlaylistLockedException.class);
    }

    @Test
    void delete_whenOwnerAndDraft_shouldRemovePlaylist() {
        Playlist playlist = playlist(7L, PlaylistStatus.DRAFT);
        visible(playlist);

        service.delete(7L, 1, 1L);

        then(playlistSongRepository).should().deleteAllOf(7L);
        then(playlistRepository).should().delete(playlist);
    }

    @Test
    void publish_whenDraftHasSongs_shouldSetPublishedAt() {
        Playlist playlist = playlist(7L, PlaylistStatus.DRAFT);
        given(playlistRepository.findById(7L)).willReturn(Optional.of(playlist));
        given(playlistSongRepository.countByIdPlaylistId(7L)).willReturn(2L);

        service.publish(7L, 1, 1L);

        assertThat(playlist.getStatus()).isEqualTo(PlaylistStatus.PUBLISHED);
        assertThat(playlist.getPublishedAt()).isNotNull();
        then(playlistRepository).should().save(playlist);
        then(auditLogRepository).should().save(any(AuditLog.class));
    }

    @Test
    void publish_whenAlreadyPublished_shouldThrowLocked() {
        given(playlistRepository.findById(7L))
                .willReturn(Optional.of(playlist(7L, PlaylistStatus.PUBLISHED)));

        assertThatThrownBy(() -> service.publish(7L, 1, 1L))
                .isInstanceOf(PlaylistLockedException.class);

        then(playlistRepository).should(never()).save(any());
    }

    @Test
    void unpublish_whenAlreadyDraft_shouldBeIdempotent() {
        Playlist playlist = playlist(7L, PlaylistStatus.DRAFT);
        given(playlistRepository.findById(7L)).willReturn(Optional.of(playlist));

        service.unpublish(7L, 1, 1L);

        assertThat(playlist.getStatus()).isEqualTo(PlaylistStatus.DRAFT);
        then(playlistRepository).should().save(playlist);
    }

    @Test
    void unpublish_shouldReopenContentEditing() {
        Playlist playlist = playlist(7L, PlaylistStatus.PUBLISHED);
        given(playlistRepository.findById(7L)).willReturn(Optional.of(playlist));
        given(playlistRepository.countVisibleTo(7L, 1L)).willReturn(1L);
        given(playlistSongRepository.existsByIdPlaylistIdAndIdSongId(7L, 42L)).willReturn(false);
        given(songRepository.findById(42L)).willReturn(Optional.of(song(42L)));
        given(playlistSongRepository.findMaxPosition(7L)).willReturn(0);

        service.unpublish(7L, 1, 1L);
        service.addSong(7L, 1, 42L, 1L);

        assertThat(playlist.getStatus()).isEqualTo(PlaylistStatus.DRAFT);
        then(playlistSongRepository).should().save(any(PlaylistSong.class));
    }

    @Test
    void transferOwnedPlaylists_whenSuccessorIsActingAdmin_shouldTransfer() {
        Playlist owned = new Playlist("Morning", 7L);
        ReflectionTestUtils.setField(owned, "id", 11L);
        given(playlistRepository.findByOwnerId(7L)).willReturn(List.of(owned));

        int moved = service.transferOwnedPlaylists(7L, Map.of(11L, 1L), 1L);

        assertThat(moved).isEqualTo(1);
        assertThat(owned.getOwnerId()).isEqualTo(1L);
        then(playlistRepository).should().deleteCollaboratorGrant(11L, 1L);
        then(playlistRepository).should().deleteCollaboratorGrantsOf(7L);
    }

    @Test
    void transferOwnedPlaylists_whenSuccessorIsDeactivated_shouldThrow() {
        Playlist owned = new Playlist("Morning", 7L);
        ReflectionTestUtils.setField(owned, "id", 11L);
        given(playlistRepository.findByOwnerId(7L)).willReturn(List.of(owned));
        given(playlistRepository.countCollaboratorGrant(11L, 15L)).willReturn(1L);
        User deactivated = designer(15L);
        deactivated.setStatus(UserStatus.DEACTIVATED);
        given(userRepository.findById(15L)).willReturn(Optional.of(deactivated));

        assertThatThrownBy(() -> service.transferOwnedPlaylists(7L, Map.of(11L, 15L), 1L))
                .isInstanceOf(InvalidSuccessorException.class);

        then(playlistRepository).should(never()).saveAll(any());
    }

    @Test
    void transferOwnedPlaylists_shouldDropTheSuccessorsRedundantGrant() {
        Playlist owned = new Playlist("Morning", 7L);
        ReflectionTestUtils.setField(owned, "id", 11L);
        given(playlistRepository.findByOwnerId(7L)).willReturn(List.of(owned));
        given(playlistRepository.countCollaboratorGrant(11L, 15L)).willReturn(1L);
        given(userRepository.findById(15L)).willReturn(Optional.of(designer(15L)));

        service.transferOwnedPlaylists(7L, Map.of(11L, 15L), 1L);

        then(playlistRepository).should().deleteCollaboratorGrant(11L, 15L);
    }

    @Test
    void successorChoices_shouldListActiveCollaborators() {
        Playlist owned = playlist(7L, PlaylistStatus.DRAFT);
        given(playlistRepository.findByOwnerId(1L)).willReturn(List.of(owned));
        given(playlistRepository.findById(7L)).willReturn(Optional.of(owned));
        PlaylistRepository.OwnerOption collab = org.mockito.Mockito.mock(
                PlaylistRepository.OwnerOption.class);
        given(collab.getId()).willReturn(15L);
        given(collab.getName()).willReturn("Dana Designer");
        given(playlistRepository.findCollaborators(7L)).willReturn(List.of(collab));

        List<PlaylistSuccessorChoice> choices = service.successorChoices(1L);

        assertThat(choices).hasSize(1);
        assertThat(choices.get(0).collaborators()).extracting(PlaylistOwner::id)
                .containsExactly(15L);
    }

    @Test
    void successorChoices_shouldExcludeCustomers() {
        Playlist owned = playlist(7L, PlaylistStatus.DRAFT);
        given(playlistRepository.findByOwnerId(1L)).willReturn(List.of(owned));
        given(playlistRepository.findById(7L)).willReturn(Optional.of(owned));
        PlaylistRepository.OwnerOption collab = org.mockito.Mockito.mock(
                PlaylistRepository.OwnerOption.class);
        given(collab.getId()).willReturn(15L);
        given(collab.getName()).willReturn("Dana Designer");
        given(playlistRepository.findCollaborators(7L)).willReturn(List.of(collab));

        List<PlaylistSuccessorChoice> choices = service.successorChoices(1L);

        assertThat(choices.get(0).collaborators()).extracting(PlaylistOwner::id)
                .doesNotContain(20L);
    }

    @Test
    void grant_whenGranteeIsDeactivated_shouldReject() {
        given(playlistRepository.findById(7L))
                .willReturn(Optional.of(playlist(7L, PlaylistStatus.DRAFT)));
        User deactivated = designer(15L);
        deactivated.setStatus(UserStatus.DEACTIVATED);
        given(userRepository.findById(15L)).willReturn(Optional.of(deactivated));

        assertThatThrownBy(() -> service.grant(7L, 1, 15L, 1L))
                .isInstanceOf(InvalidCollaboratorException.class);

        then(playlistRepository).should(never()).insertCollaboratorGrant(any(), any(), any());
    }

    @Test
    void grant_whenActorIsAdmin_shouldSucceed() {
        Playlist playlist = playlist(7L, PlaylistStatus.DRAFT);
        given(playlistRepository.findById(7L)).willReturn(Optional.of(playlist));
        given(userRepository.findById(99L)).willReturn(Optional.of(admin(99L)));
        given(userRepository.findById(15L)).willReturn(Optional.of(designer(15L)));
        given(playlistRepository.countCollaboratorGrant(7L, 15L)).willReturn(0L);

        service.grant(7L, 1, 15L, 99L);

        then(playlistRepository).should().insertCollaboratorGrant(7L, 15L, 99L);
    }

    @Test
    void revoke_whenOwner_shouldRemoveGrant() {
        given(playlistRepository.findById(7L))
                .willReturn(Optional.of(playlist(7L, PlaylistStatus.DRAFT)));
        given(playlistRepository.countCollaboratorGrant(7L, 15L)).willReturn(1L);

        service.revoke(7L, 1, 15L, 1L);

        then(playlistRepository).should().deleteCollaboratorGrant(7L, 15L);
    }

    @Test
    void revoke_whenActorIsCollaborator_shouldReject() {
        given(playlistRepository.findById(7L))
                .willReturn(Optional.of(playlist(7L, PlaylistStatus.DRAFT)));
        given(userRepository.findById(15L)).willReturn(Optional.of(designer(15L)));
        given(playlistRepository.countVisibleTo(7L, 15L)).willReturn(1L);

        assertThatThrownBy(() -> service.revoke(7L, 1, 20L, 15L))
                .isInstanceOf(InvalidCollaboratorException.class);

        then(playlistRepository).should(never()).deleteCollaboratorGrant(any(), any());
    }

    @Test
    void revoke_whenGrantMissing_shouldThrow() {
        given(playlistRepository.findById(7L))
                .willReturn(Optional.of(playlist(7L, PlaylistStatus.DRAFT)));
        given(playlistRepository.countCollaboratorGrant(7L, 15L)).willReturn(0L);

        assertThatThrownBy(() -> service.revoke(7L, 1, 15L, 1L))
                .isInstanceOf(InvalidCollaboratorException.class);
    }

    /**
     * BR-06 across the whole mutation surface: whoever submits the older
     * version is refused, and refused before anything is written, so the save
     * that got there first stands untouched.
     */
    @Test
    void rename_whenVersionStale_shouldThrowWithoutWriting() {
        visible(atVersion(4));

        assertThatThrownBy(() -> service.rename(7L, 3, "Evening tea", 1L))
                .isInstanceOf(StalePlaylistException.class);

        then(playlistRepository).should(never()).save(any());
    }

    @Test
    void addSongs_whenVersionStale_shouldThrowWithoutWriting() {
        visible(atVersion(4));

        assertThatThrownBy(() -> service.addSongs(7L, 3, List.of(42L), 1L))
                .isInstanceOf(StalePlaylistException.class);

        then(playlistSongRepository).should(never()).save(any());
        then(playlistRepository).should(never()).save(any());
    }

    @Test
    void removeSong_whenVersionStale_shouldThrowWithoutWriting() {
        visible(atVersion(4));

        assertThatThrownBy(() -> service.removeSong(7L, 3, 42L, 1L))
                .isInstanceOf(StalePlaylistException.class);

        then(playlistSongRepository).should(never()).deleteSong(any(), any());
    }

    @Test
    void move_whenVersionStale_shouldThrowWithoutWriting() {
        visible(atVersion(4));

        assertThatThrownBy(() -> service.move(7L, 3, 42L, true, 1L))
                .isInstanceOf(StalePlaylistException.class);

        then(playlistSongRepository).should(never()).moveOne(any(), anyInt(), anyInt());
    }

    @Test
    void delete_whenVersionStale_shouldThrowWithoutWriting() {
        visible(atVersion(4));

        assertThatThrownBy(() -> service.delete(7L, 3, 1L))
                .isInstanceOf(StalePlaylistException.class);

        then(playlistSongRepository).should(never()).deleteAllOf(any());
        then(playlistRepository).should(never()).delete(any());
    }

    @Test
    void publish_whenVersionStale_shouldThrowWithoutWriting() {
        given(playlistRepository.findById(7L)).willReturn(Optional.of(atVersion(4)));

        assertThatThrownBy(() -> service.publish(7L, 3, 1L))
                .isInstanceOf(StalePlaylistException.class);

        then(playlistRepository).should(never()).save(any());
    }

    @Test
    void unpublish_whenVersionStale_shouldThrowWithoutWriting() {
        given(playlistRepository.findById(7L)).willReturn(Optional.of(atVersion(4)));

        assertThatThrownBy(() -> service.unpublish(7L, 3, 1L))
                .isInstanceOf(StalePlaylistException.class);

        then(playlistRepository).should(never()).save(any());
    }

    @Test
    void grant_whenVersionStale_shouldThrowWithoutWriting() {
        given(playlistRepository.findById(7L)).willReturn(Optional.of(atVersion(4)));

        assertThatThrownBy(() -> service.grant(7L, 3, 15L, 1L))
                .isInstanceOf(StalePlaylistException.class);

        then(playlistRepository).should(never()).insertCollaboratorGrant(any(), any(), any());
    }

    @Test
    void revoke_whenVersionStale_shouldThrowWithoutWriting() {
        given(playlistRepository.findById(7L)).willReturn(Optional.of(atVersion(4)));

        assertThatThrownBy(() -> service.revoke(7L, 3, 15L, 1L))
                .isInstanceOf(StalePlaylistException.class);

        then(playlistRepository).should(never()).deleteCollaboratorGrant(any(), any());
    }

    /** The exception carries both versions so the screen can report them (NAC-02). */
    @Test
    void aStaleSave_shouldReportBothVersions() {
        visible(atVersion(4));

        assertThatThrownBy(() -> service.rename(7L, 3, "Evening tea", 1L))
                .isInstanceOfSatisfying(StalePlaylistException.class, e -> {
                    assertThat(e.getPlaylistId()).isEqualTo(7L);
                    assertThat(e.getExpectedVersion()).isEqualTo(3);
                    assertThat(e.getCurrentVersion()).isEqualTo(4);
                });
    }

    /**
     * A collaborator grant is not part of the content, but it decides who may
     * change the content, so revoking one has to move the version too.
     */
    @Test
    void revoke_shouldTouchThePlaylistSoTheVersionMoves() {
        Playlist playlist = playlist(7L, PlaylistStatus.DRAFT);
        given(playlistRepository.findById(7L)).willReturn(Optional.of(playlist));
        given(playlistRepository.countCollaboratorGrant(7L, 15L)).willReturn(1L);

        service.revoke(7L, 1, 15L, 1L);

        then(playlistRepository).should().save(playlist);
    }

    /** UC-19 A2: the copy carries the change and the source is only read. */
    @Test
    void cloneOnConflict_whenRemoveWasRejected_shouldLeaveTheSongOutOfTheCopy() {
        Playlist source = playlist(7L, PlaylistStatus.DRAFT);
        visible(source);
        given(playlistSongRepository.findOrdered(7L)).willReturn(List.of(
                new PlaylistSong(7L, song(42L), 1),
                new PlaylistSong(7L, song(43L), 2)));
        given(playlistRepository.save(any(Playlist.class)))
                .willAnswer(call -> call.getArgument(0));
        given(songRepository.findById(43L)).willReturn(Optional.of(song(43L)));

        service.cloneOnConflict(7L, "Morning coffee (copy)", 1L, PendingEdit.removeSong(42L));

        // Only the song that survives the pending removal is copied across.
        then(songRepository).should(never()).findById(42L);
        then(songRepository).should().findById(43L);
    }

    @Test
    void exportCsv_shouldStartWithHeaderRow() {
        visible(playlist(7L, PlaylistStatus.DRAFT));
        given(playlistSongRepository.findOrdered(7L)).willReturn(List.of());

        String csv = service.exportCsv(7L, 1L);

        assertThat(csv).startsWith("position,title,artist,duration_seconds,provider\r\n");
    }

    @Test
    void exportCsv_whenCallerCannotView_shouldThrow() {
        given(playlistRepository.findById(7L))
                .willReturn(Optional.of(playlist(7L, PlaylistStatus.DRAFT)));
        given(playlistRepository.countVisibleTo(7L, 99L)).willReturn(0L);

        assertThatThrownBy(() -> service.exportCsv(7L, 99L))
                .isInstanceOf(PlaylistNotFoundException.class);
    }

    @Test
    void exportCsv_whenDurationIsNull_shouldLeaveDurationBlank() {
        visible(playlist(7L, PlaylistStatus.DRAFT));
        Song song = song(42L);
        song.setDuration(null);
        given(playlistSongRepository.findOrdered(7L))
                .willReturn(List.of(new PlaylistSong(7L, song, 1)));

        String csv = service.exportCsv(7L, 1L);

        assertThat(csv).contains("1,\"Ice Cream\",\"Sugar Blizz\",,\"EpidemicSound\"");
    }

    @Test
    void exportCsv_whenPlaylistEmpty_shouldReturnHeaderOnly() {
        visible(playlist(7L, PlaylistStatus.DRAFT));
        given(playlistSongRepository.findOrdered(7L)).willReturn(List.of());

        assertThat(service.exportCsv(7L, 1L))
                .isEqualTo("position,title,artist,duration_seconds,provider\r\n");
    }

    private static PlaylistRepository.SummaryRow summary(Long id, String name, String status,
            Long ownerId) {
        PlaylistRepository.SummaryRow row = org.mockito.Mockito.mock(
                PlaylistRepository.SummaryRow.class);
        given(row.getId()).willReturn(id);
        given(row.getName()).willReturn(name);
        given(row.getStatus()).willReturn(status);
        given(row.getVersion()).willReturn(1);
        given(row.getOwnerId()).willReturn(ownerId);
        given(row.getOwnerName()).willReturn("Dana Designer");
        return row;
    }

    private static PlaylistRepository.PublishedCardRow publishedCard(Long id, String name) {
        PlaylistRepository.PublishedCardRow row = org.mockito.Mockito.mock(
                PlaylistRepository.PublishedCardRow.class);
        given(row.getId()).willReturn(id);
        given(row.getName()).willReturn(name);
        given(row.getOwnerName()).willReturn("Dana Designer");
        given(row.getPublishedAt()).willReturn(LocalDateTime.of(2026, 9, 1, 10, 0));
        given(row.getSongCount()).willReturn(3L);
        return row;
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

    private static User admin(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("System Admin");
        user.setEmail("admin@mrs.local");
        user.setRole(Role.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
