package com.funix.swp490x.mrs.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funix.swp490x.mrs.config.SecurityConfig;
import com.funix.swp490x.mrs.config.WebConfig;
import com.funix.swp490x.mrs.domain.Playlist;
import com.funix.swp490x.mrs.domain.PlaylistSong;
import com.funix.swp490x.mrs.domain.PlaylistStatus;
import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.LoginAttemptService;
import com.funix.swp490x.mrs.security.LoginFailureHandler;
import com.funix.swp490x.mrs.security.LoginSuccessHandler;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.security.MrsUserDetailsService;
import com.funix.swp490x.mrs.service.DuplicateCollaboratorException;
import com.funix.swp490x.mrs.service.DuplicatePlaylistNameException;
import com.funix.swp490x.mrs.service.DuplicatePlaylistSongException;
import com.funix.swp490x.mrs.service.InvalidCollaboratorException;
import com.funix.swp490x.mrs.service.PlaylistLockedException;
import com.funix.swp490x.mrs.service.PlaylistNotFoundException;
import com.funix.swp490x.mrs.service.PlaylistService;
import com.funix.swp490x.mrs.service.PlaylistSummary;
import com.funix.swp490x.mrs.service.SongNotFoundException;
import com.funix.swp490x.mrs.service.StalePlaylistException;
import com.funix.swp490x.mrs.web.support.ShellModelAdvice;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * P-03a My Playlists and the Add-to-playlist loop that reaches it from the
 * Songs table (FT-06).
 *
 * <p>Runs on the web layer only, so no database is needed.
 */
@WebMvcTest(controllers = PlaylistController.class)
@Import({SecurityConfig.class, WebConfig.class, ShellModelAdvice.class, LoginSuccessHandler.class,
        LoginFailureHandler.class, LoginAttemptService.class, MrsUserDetailsService.class})
class PlaylistFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PlaylistService playlistService;

    @BeforeEach
    void defaults() {
        given(playlistService.search(nullable(Long.class), nullable(PlaylistStatus.class),
                nullable(String.class), anyInt())).willReturn(Page.empty());
        given(playlistService.view(nullable(Long.class), nullable(Long.class)))
                .willReturn(draft("Morning coffee"));
        given(playlistService.songs(nullable(Long.class), nullable(Long.class)))
                .willReturn(List.of());
    }

    @Test
    void theListShowsOwnedAndSharedPlaylists() throws Exception {
        given(playlistService.search(eq(1L), nullable(PlaylistStatus.class),
                nullable(String.class), anyInt()))
                .willReturn(new PageImpl<>(List.of(
                        summary(7L, "Morning coffee", PlaylistStatus.DRAFT, false),
                        summary(8L, "Launch party", PlaylistStatus.PUBLISHED, true),
                        summary(9L, "Shared draft", PlaylistStatus.DRAFT, true)),
                        PageRequest.of(0, 20), 3));

        mockMvc.perform(get(Routes.PLAYLISTS).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Morning coffee")))
                .andExpect(content().string(containsString("Launch party")))
                .andExpect(content().string(containsString("Shared with me")))
                .andExpect(content().string(containsString("New playlist")))
                .andExpect(content().string(containsString("data-rename-playlist")))
                .andExpect(content().string(containsString("id=\"renamePlaylist\"")))
                .andExpect(content().string(containsString("data-duplicate-playlist")))
                .andExpect(content().string(containsString("id=\"duplicatePlaylist\"")))
                .andExpect(content().string(containsString("/playlists/7/delete")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("/playlists/9/delete"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("Open playlist"))));
    }

    /** FT-06 NAC-03: a Customer reads shared work in the Workspace; My Playlists is curator-only. */
    @Test
    void aCustomerCannotOpenMyPlaylists() throws Exception {
        mockMvc.perform(get(Routes.PLAYLISTS).with(user(principal(Role.CUSTOMER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void theStatusAndNameFiltersReachTheService() throws Exception {
        mockMvc.perform(get(Routes.PLAYLISTS)
                        .param("status", "DRAFT")
                        .param("q", "coffee")
                        .param("page", "1")
                        .with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk());

        then(playlistService).should().search(1L, PlaylistStatus.DRAFT, "coffee", 1);
    }

    @Test
    void addingASongReturnsToTheScreenItWasStartedFrom() throws Exception {
        given(playlistService.addSongs(7L, 1, List.of(42L), 1L)).willReturn(1);

        mockMvc.perform(post("/playlists/7/songs")
                        .param("expectedVersion", "1")
                        .param("songId", "42")
                        .param("returnTo", "/songs?q=ice")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/songs?q=ice"))
                .andExpect(flash().attribute("flash", Messages.SONG_ADDED_TO_PLAYLIST));

        then(playlistService).should().addSongs(7L, 1, List.of(42L), 1L);
    }

    @Test
    void creatingAPlaylistFromTheDialogAddsTheSongToo() throws Exception {
        given(playlistService.createWithSong(1L, "Morning coffee", 42L))
                .willReturn(draft("Morning coffee"));

        mockMvc.perform(post(Routes.PLAYLISTS)
                        .param("name", "Morning coffee")
                        .param("songId", "42")
                        .param("returnTo", "/admin/catalog")
                        .with(user(principal(Role.ADMIN)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/admin/catalog"))
                .andExpect(flash().attribute("flash", Messages.PLAYLIST_CREATED_WITH_SONG));
    }

    /** Without a song the dialog is not involved, so open the new playlist. */
    @Test
    void creatingAPlaylistOnItsOwnOpensIt() throws Exception {
        given(playlistService.create(1L, "Morning coffee")).willReturn(draft("Morning coffee"));

        mockMvc.perform(post(Routes.PLAYLISTS)
                        .param("name", "Morning coffee")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/playlists/5"));
    }

    @Test
    void renamingAPlaylistFromTheListStaysOnTheList() throws Exception {
        mockMvc.perform(post("/playlists/7/rename")
                        .param("expectedVersion", "1")
                        .param("name", "Evening tea")
                        .param("returnTo", "/playlists")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(redirectedUrl(Routes.PLAYLISTS))
                .andExpect(flash().attribute("flash", Messages.PLAYLIST_RENAMED));

        then(playlistService).should().rename(7L, 1, "Evening tea", 1L);
    }

    @Test
    void duplicatingAPlaylistOpensTheCopy() throws Exception {
        Playlist copy = draft("Morning coffee (copy)");
        ReflectionTestUtils.setField(copy, "id", 11L);
        given(playlistService.duplicate(7L, "Morning coffee (copy)", 1L)).willReturn(copy);

        mockMvc.perform(post("/playlists/7/duplicate")
                        .param("name", "Morning coffee (copy)")
                        .param("returnTo", "/workspace/7")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/playlists/11"))
                .andExpect(flash().attribute("flash", Messages.PLAYLIST_DUPLICATED));

        then(playlistService).should().duplicate(7L, "Morning coffee (copy)", 1L);
    }

    @Test
    void aTakenNameOnDuplicateStaysOnTheScreenItCameFrom() throws Exception {
        willThrow(new DuplicatePlaylistNameException("Morning coffee (copy)"))
                .given(playlistService).duplicate(7L, "Morning coffee (copy)", 1L);

        mockMvc.perform(post("/playlists/7/duplicate")
                        .param("name", "Morning coffee (copy)")
                        .param("returnTo", "/workspace/7")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/workspace/7"))
                .andExpect(flash().attribute("flash", Messages.PLAYLIST_NAME_TAKEN));
    }

    @Test
    void aTakenNameOnCreateIsReported() throws Exception {
        willThrow(new DuplicatePlaylistNameException("Morning coffee"))
                .given(playlistService).create(1L, "Morning coffee");

        mockMvc.perform(post(Routes.PLAYLISTS)
                        .param("name", "Morning coffee")
                        .param("returnTo", "/playlists")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(redirectedUrl(Routes.PLAYLISTS))
                .andExpect(flash().attribute("flash", Messages.PLAYLIST_NAME_TAKEN));
    }

    @Test
    void renamingAPublishedPlaylistIsRefused() throws Exception {
        willThrow(new PlaylistLockedException(7L))
                .given(playlistService).rename(7L, 1, "Evening tea", 1L);

        mockMvc.perform(post("/playlists/7/rename")
                        .param("expectedVersion", "1")
                        .param("name", "Evening tea")
                        .param("returnTo", "/playlists")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(flash().attribute("flash", Messages.PLAYLIST_LOCKED));
    }

    /** The service swallows the duplicate and reports nothing was appended. */
    @Test
    void aSongAlreadyInThePlaylistIsReportedRatherThanDuplicated() throws Exception {
        given(playlistService.addSongs(7L, 1, List.of(42L), 1L)).willReturn(0);

        mockMvc.perform(post("/playlists/7/songs")
                        .param("expectedVersion", "1")
                        .param("songId", "42")
                        .param("returnTo", "/songs")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/songs"))
                .andExpect(flash().attribute("flash", Messages.SONG_ALREADY_IN_PLAYLIST));
    }

    /** DC-08: a Published playlist takes no new songs until it is unpublished. */
    @Test
    void aPublishedPlaylistRefusesTheAdd() throws Exception {
        willThrow(new PlaylistLockedException(7L))
                .given(playlistService).addSongs(7L, 1, List.of(42L), 1L);

        mockMvc.perform(post("/playlists/7/songs")
                        .param("expectedVersion", "1")
                        .param("songId", "42")
                        .param("returnTo", "/songs")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(flash().attribute("flash", Messages.PLAYLIST_LOCKED));
    }

    /**
     * The dialog opens from several screens, so it names where to go back to.
     * Anything else is dropped rather than followed.
     */
    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example/steal", "//evil.example", "/etc/passwd", ""})
    void anUnknownReturnTargetFallsBackToMyPlaylists(String returnTo) throws Exception {
        mockMvc.perform(post("/playlists/7/songs")
                        .param("expectedVersion", "1")
                        .param("songId", "42")
                        .param("returnTo", returnTo)
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(redirectedUrl(Routes.PLAYLISTS));
    }

    /** The sidebar hiding the button is usability; this is the real rule. */
    @Test
    void aCustomerCannotPostToAPlaylist() throws Exception {
        mockMvc.perform(post(Routes.PLAYLISTS)
                        .param("name", "Mine")
                        .with(user(principal(Role.CUSTOMER)))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void theDetailPageListsSongsInOrderWithTheEditorControls() throws Exception {
        given(playlistService.songs(5L, 1L)).willReturn(List.of(
                new PlaylistSong(5L, song(42L, "Ice Cream", 213), 1),
                new PlaylistSong(5L, song(43L, "Sun Chaser", 65), 2)));
        given(playlistService.totalDuration(5L)).willReturn(278L);

        mockMvc.perform(get("/playlists/5").with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ice Cream")))
                .andExpect(content().string(containsString("3:33")))
                .andExpect(content().string(containsString("1:05")))
                .andExpect(content().string(containsString("4:38")))
                .andExpect(content().string(containsString("data-preview-url")))
                .andExpect(content().string(containsString("/playlists/5/songs/42/move")))
                .andExpect(content().string(containsString("/playlists/5/songs/43/remove")))
                .andExpect(content().string(containsString("Publish")))
                .andExpect(content().string(containsString(
                        "Publish this playlist? It will appear in the Shared Workspace")))
                .andExpect(content().string(containsString("data-rename-playlist")))
                .andExpect(content().string(containsString("data-duplicate-playlist")));
    }

    @Test
    void aCollaboratorDoesNotSeeDeleteShareOrPublishControls() throws Exception {
        Playlist shared = new Playlist("Morning coffee", 9L);
        ReflectionTestUtils.setField(shared, "id", 5L);
        given(playlistService.view(5L, 1L)).willReturn(shared);
        given(playlistService.songs(5L, 1L)).willReturn(List.of(
                new PlaylistSong(5L, song(42L, "Ice Cream", 213), 1)));

        mockMvc.perform(get("/playlists/5").with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Collaborators")))
                .andExpect(content().string(containsString("/playlists/5/songs/42/remove")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("/playlists/5/delete"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("/playlists/5/publish"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("Add a Content Designer"))));
    }

    @Test
    void aCollaboratorDoesNotSeeUnpublishOnAPublishedPlaylist() throws Exception {
        Playlist shared = new Playlist("Launch party", 9L);
        shared.setStatus(PlaylistStatus.PUBLISHED);
        ReflectionTestUtils.setField(shared, "id", 5L);
        given(playlistService.view(5L, 1L)).willReturn(shared);

        mockMvc.perform(get("/playlists/5").with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Published playlists are locked")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("/playlists/5/unpublish"))));
    }

    @Test
    void theOwnerCanGrantACollaborator() throws Exception {
        mockMvc.perform(post("/playlists/5/collaborators")
                        .param("expectedVersion", "1")
                        .param("userId", "15")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/playlists/5"))
                .andExpect(flash().attribute("flash", Messages.COLLABORATOR_ADDED));

        then(playlistService).should().grant(5L, 1, 15L, 1L);
    }

    @Test
    void aDuplicateGrantIsRejected() throws Exception {
        willThrow(new DuplicateCollaboratorException(5L, 15L))
                .given(playlistService).grant(5L, 1, 15L, 1L);

        mockMvc.perform(post("/playlists/5/collaborators")
                        .param("expectedVersion", "1")
                        .param("userId", "15")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(flash().attribute("flash", Messages.COLLABORATOR_ALREADY));
    }

    @Test
    void aCollaboratorCannotGrantAnother() throws Exception {
        willThrow(new InvalidCollaboratorException("Only the owner can share this playlist"))
                .given(playlistService).grant(5L, 1, 15L, 1L);

        mockMvc.perform(post("/playlists/5/collaborators")
                        .param("expectedVersion", "1")
                        .param("userId", "15")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(flash().attribute("flash", Messages.COLLABORATOR_MANAGE_OWNER_ONLY));
    }

    @Test
    void aCollaboratorCannotDelete() throws Exception {
        willThrow(new InvalidCollaboratorException("Only the owner can delete this playlist"))
                .given(playlistService).delete(5L, 1, 1L);

        mockMvc.perform(post("/playlists/5/delete")
                        .param("expectedVersion", "1")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/playlists/5"))
                .andExpect(flash().attribute("flash", Messages.PLAYLIST_DELETE_NOT_OWNER));
    }

    @Test
    void theOwnerCanStillPublish() throws Exception {
        mockMvc.perform(post("/playlists/5/publish")
                        .param("expectedVersion", "1")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/playlists/5"))
                .andExpect(flash().attribute("flash", Messages.PLAYLIST_PUBLISHED));

        then(playlistService).should().publish(5L, 1, 1L);
    }

    @Test
    void anAdminPublishReturnsToInspect() throws Exception {
        mockMvc.perform(post("/playlists/5/publish")
                        .param("expectedVersion", "1")
                        .param("returnTo", "/admin/playlists/5")
                        .with(user(principal(Role.ADMIN)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/admin/playlists/5"))
                .andExpect(flash().attribute("flash", Messages.PLAYLIST_PUBLISHED));

        then(playlistService).should().publish(5L, 1, 1L);
    }

    @Test
    void aCollaboratorCannotPublish() throws Exception {
        willThrow(new InvalidCollaboratorException("Only the owner can publish this playlist"))
                .given(playlistService).publish(5L, 1, 1L);

        mockMvc.perform(post("/playlists/5/publish")
                        .param("expectedVersion", "1")
                        .param("returnTo", "/admin/playlists/5")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/playlists/5"))
                .andExpect(flash().attribute("flash", Messages.PLAYLIST_PUBLISH_NOT_OWNER));
    }

    @Test
    void aCollaboratorCannotUnpublish() throws Exception {
        willThrow(new InvalidCollaboratorException("Only the owner can unpublish this playlist"))
                .given(playlistService).unpublish(5L, 1, 1L);

        mockMvc.perform(post("/playlists/5/unpublish")
                        .param("expectedVersion", "1")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/playlists/5"))
                .andExpect(flash().attribute("flash", Messages.PLAYLIST_UNPUBLISH_NOT_OWNER));
    }

    /** DC-08: a Published playlist shows the reason, and none of the controls. */
    @Test
    void aPublishedPlaylistSaysItIsLockedAndDropsTheEditors() throws Exception {
        Playlist published = draft("Launch party");
        published.setStatus(PlaylistStatus.PUBLISHED);
        given(playlistService.view(5L, 1L)).willReturn(published);
        given(playlistService.songs(5L, 1L))
                .willReturn(List.of(new PlaylistSong(5L, song(42L, "Ice Cream", 213), 1)));

        mockMvc.perform(get("/playlists/5").with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Published playlists are locked")))
                .andExpect(content().string(containsString("Unpublish")))
                .andExpect(content().string(containsString(
                        "Unpublish this playlist? It will leave the Shared Workspace")))
                .andExpect(content().string(containsString("data-duplicate-playlist")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("/playlists/5/songs/42/remove"))));
    }

    @Test
    void aPlaylistThatIsNotSharedWithTheCallerReadsAsMissing() throws Exception {
        given(playlistService.view(9L, 1L)).willThrow(new PlaylistNotFoundException(9L));

        mockMvc.perform(get("/playlists/9").with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportSendsCsvAsAnAttachment() throws Exception {
        given(playlistService.viewExportable(5L, 1L)).willReturn(draft("Morning coffee"));
        given(playlistService.exportCsv(5L, 1L))
                .willReturn("position,title\r\n1,\"Ice Cream\"\r\n");

        mockMvc.perform(get("/playlists/5/export.csv")
                        .with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ice Cream")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Disposition",
                                containsString("Morning-coffee.csv")));
    }

    /**
     * UC-19 normal flow: HTTP 409, MSG_014 and the current version, rendered in
     * place rather than redirected, so the rejected change is still there to
     * clone.
     */
    @Test
    void aStaleSaveReturns409WithTheConflictScreen() throws Exception {
        willThrow(new StalePlaylistException(5L, 3, 7))
                .given(playlistService).rename(5L, 3, "Evening tea", 1L);
        given(playlistService.inspect(5L)).willReturn(draft("Morning coffee"));

        mockMvc.perform(post("/playlists/5/rename")
                        .param("expectedVersion", "3")
                        .param("name", "Evening tea")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString(Messages.PLAYLIST_STALE)))
                .andExpect(content().string(containsString("Refresh &amp; Reapply")))
                .andExpect(content().string(containsString("Clone as New Playlist")))
                // The name the rename was reaching for becomes the copy's name.
                .andExpect(content().string(containsString("Evening tea")));
    }

    /** BR-11 is about the requester's own edits; publishing has none to carry. */
    @Test
    void aStalePublishOffersRefreshButNotClone() throws Exception {
        willThrow(new StalePlaylistException(5L, 3, 7))
                .given(playlistService).publish(5L, 3, 1L);
        given(playlistService.inspect(5L)).willReturn(draft("Morning coffee"));

        mockMvc.perform(post("/playlists/5/publish")
                        .param("expectedVersion", "3")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Refresh &amp; Reapply")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("Clone as New Playlist"))));
    }

    @Test
    void aStaleSongRemovalOffersACloneThatKeepsTheSongId() throws Exception {
        willThrow(new StalePlaylistException(5L, 3, 7))
                .given(playlistService).removeSong(5L, 3, 42L, 1L);
        given(playlistService.inspect(5L)).willReturn(draft("Morning coffee"));

        mockMvc.perform(post("/playlists/5/songs/42/remove")
                        .param("expectedVersion", "3")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("/playlists/5/clone-on-conflict")))
                .andExpect(content().string(containsString("name=\"songId\"")))
                .andExpect(content().string(containsString("REMOVE_SONG")));
    }

    @Test
    void cloningOnConflictOpensTheNewDraft() throws Exception {
        Playlist copy = draft("Morning coffee (copy)");
        ReflectionTestUtils.setField(copy, "id", 11L);
        given(playlistService.cloneOnConflict(eq(5L), eq("Morning coffee (copy)"), eq(1L),
                org.mockito.ArgumentMatchers.any())).willReturn(copy);

        mockMvc.perform(post("/playlists/5/clone-on-conflict")
                        .param("name", "Morning coffee (copy)")
                        .param("kind", "REMOVE_SONG")
                        .param("songId", "42")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/playlists/11"))
                .andExpect(flash().attribute("flash", Messages.PLAYLIST_CLONED_FROM_CONFLICT));

        then(playlistService).should().cloneOnConflict(eq(5L), eq("Morning coffee (copy)"), eq(1L),
                org.mockito.ArgumentMatchers.any());
    }

    /** UC-19 E2: cloning from a source that has since gone reads as missing. */
    @Test
    void cloningFromADeletedSourceIsNotFound() throws Exception {
        given(playlistService.cloneOnConflict(eq(5L), eq("Morning coffee (copy)"), eq(1L),
                org.mockito.ArgumentMatchers.any()))
                .willThrow(new PlaylistNotFoundException(5L));

        mockMvc.perform(post("/playlists/5/clone-on-conflict")
                        .param("name", "Morning coffee (copy)")
                        .param("kind", "MOVE_SONG")
                        .param("songId", "42")
                        .param("up", "true")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    /** The copy needs a free name; a taken one comes back to the same screen. */
    @Test
    void aTakenNameOnACloneStaysOnTheConflictScreen() throws Exception {
        given(playlistService.inspect(5L)).willReturn(draft("Morning coffee"));
        given(playlistService.cloneOnConflict(eq(5L), eq("Launch party"), eq(1L),
                org.mockito.ArgumentMatchers.any()))
                .willThrow(new DuplicatePlaylistNameException("Launch party"));

        mockMvc.perform(post("/playlists/5/clone-on-conflict")
                        .param("name", "Launch party")
                        .param("kind", "REMOVE_SONG")
                        .param("songId", "42")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString(Messages.PLAYLIST_NAME_TAKEN)));
    }

    /** The song left the catalog between the conflict and the clone. */
    @Test
    void aCloneOfASongThatHasSinceGoneStaysOnTheConflictScreen() throws Exception {
        given(playlistService.inspect(5L)).willReturn(draft("Morning coffee"));
        given(playlistService.cloneOnConflict(eq(5L), eq("Morning coffee (copy)"), eq(1L),
                org.mockito.ArgumentMatchers.any()))
                .willThrow(new SongNotFoundException(42L));

        mockMvc.perform(post("/playlists/5/clone-on-conflict")
                        .param("name", "Morning coffee (copy)")
                        .param("kind", "ADD_SONG")
                        .param("songId", "42")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString(Messages.SONG_NOT_FOUND)));
    }

    /** A hand built clone of a lifecycle action has nothing to carry over. */
    @Test
    void cloningANonContentEditIsRefused() throws Exception {
        mockMvc.perform(post("/playlists/5/clone-on-conflict")
                        .param("name", "Morning coffee (copy)")
                        .param("kind", "PUBLISH")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    /** The forms have to submit the version, or nothing above can fire. */
    @Test
    void theDetailFormsCarryTheVersionTheyWereRenderedAt() throws Exception {
        given(playlistService.songs(5L, 1L)).willReturn(List.of(
                new PlaylistSong(5L, song(42L, "Ice Cream", 213), 1)));

        mockMvc.perform(get("/playlists/5").with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"expectedVersion\"")))
                .andExpect(content().string(containsString("data-playlist-version")));
    }

    private static PlaylistSummary summary(Long id, String name, PlaylistStatus status,
            boolean shared) {
        return new PlaylistSummary(id, name, status, 3, 1, LocalDateTime.now(), "Dana Designer",
                shared ? 1 : 0, shared, "Dana Designer");
    }

    private static Song song(Long id, String title, int duration) {
        Song song = new Song();
        song.setId(id);
        song.setTitle(title);
        song.setArtist("Sugar Blizz");
        song.setSourceProvider("EpidemicSound");
        song.setAudioUrl("https://cdn.example/" + id + ".mp3");
        song.setDuration(duration);
        return song;
    }

    private static Playlist draft(String name) {
        Playlist playlist = new Playlist(name, 1L);
        playlist.touch(1L);
        ReflectionTestUtils.setField(playlist, "id", 5L);
        return playlist;
    }

    private static MrsUserDetails principal(Role role) {
        User user = new User();
        user.setId(1L);
        user.setUsername("Test " + role.getDisplayName());
        user.setEmail(role.name().toLowerCase() + "@mrs.local");
        user.setPasswordHash("{noop}irrelevant");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(false);
        return new MrsUserDetails(user, true);
    }
}
