package com.funix.swp490x.mrs.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import com.funix.swp490x.mrs.service.PlaylistNotFoundException;
import com.funix.swp490x.mrs.service.PlaylistService;
import com.funix.swp490x.mrs.service.PublishedOwner;
import com.funix.swp490x.mrs.service.PublishedPlaylistCard;
import com.funix.swp490x.mrs.web.support.ShellModelAdvice;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * P-04a Shared Workspace: published playlists appear here; drafts do not.
 */
@WebMvcTest(controllers = {WorkspaceController.class, PlaylistController.class})
@Import({SecurityConfig.class, WebConfig.class, ShellModelAdvice.class, LoginSuccessHandler.class,
        LoginFailureHandler.class, LoginAttemptService.class, MrsUserDetailsService.class})
class WorkspaceFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PlaylistService playlistService;

    @BeforeEach
    void defaults() {
        given(playlistService.searchPublished(nullable(Long.class), nullable(String.class), anyInt()))
                .willReturn(Page.empty());
        given(playlistService.publishedOwners()).willReturn(List.of());
        given(playlistService.search(nullable(Long.class), nullable(PlaylistStatus.class),
                nullable(String.class), anyInt())).willReturn(Page.empty());
    }

    @Test
    void publishedPlaylistsAppearAsCards() throws Exception {
        given(playlistService.searchPublished(nullable(Long.class), nullable(String.class), anyInt()))
                .willReturn(new PageImpl<>(List.of(card("Morning coffee")),
                        PageRequest.of(0, 12), 1));

        mockMvc.perform(get(Routes.WORKSPACE).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Morning coffee")))
                .andExpect(content().string(containsString("/workspace/7")))
                .andExpect(content().string(containsString("Dana Designer")))
                .andExpect(content().string(not(containsString("zone-placeholder"))));
    }

    @Test
    void customersSeeTheSamePublishedList() throws Exception {
        given(playlistService.searchPublished(nullable(Long.class), nullable(String.class), anyInt()))
                .willReturn(new PageImpl<>(List.of(card("Morning coffee")),
                        PageRequest.of(0, 12), 1));

        mockMvc.perform(get(Routes.WORKSPACE).with(user(principal(Role.CUSTOMER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Morning coffee")));
    }

    @Test
    void nameAndOwnerFiltersReachTheService() throws Exception {
        mockMvc.perform(get(Routes.WORKSPACE)
                        .param("q", "coffee")
                        .param("ownerId", "3")
                        .param("page", "1")
                        .with(user(principal(Role.ADMIN))))
                .andExpect(status().isOk());

        then(playlistService).should().searchPublished(3L, "coffee", 1);
    }

    @Test
    void thePublishedViewIsReadOnly() throws Exception {
        given(playlistService.viewPublished(7L)).willReturn(published("Morning coffee"));
        given(playlistService.publishedSongs(7L))
                .willReturn(List.of(new PlaylistSong(7L, song(42L, "Ice Cream", 213), 1)));
        given(playlistService.ownerName(1L)).willReturn("Dana Designer");
        given(playlistService.totalDuration(7L)).willReturn(213L);

        mockMvc.perform(get("/workspace/7").with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Morning coffee")))
                .andExpect(content().string(containsString("Ice Cream")))
                .andExpect(content().string(containsString("Export CSV")))
                .andExpect(content().string(containsString("Duplicate")))
                .andExpect(content().string(containsString("data-duplicate-playlist")))
                .andExpect(content().string(containsString(
                        "Unpublish this playlist? It will leave the Shared Workspace")))
                .andExpect(content().string(not(containsString("/playlists/7/songs/42/remove"))));
    }

    @Test
    void customersCannotExportFromThePublishedView() throws Exception {
        given(playlistService.viewPublished(7L)).willReturn(published("Morning coffee"));
        given(playlistService.publishedSongs(7L)).willReturn(List.of());
        given(playlistService.ownerName(1L)).willReturn("Dana Designer");

        mockMvc.perform(get("/workspace/7").with(user(principal(Role.CUSTOMER))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Export CSV"))))
                .andExpect(content().string(not(containsString("Duplicate"))))
                .andExpect(content().string(not(containsString("Unpublish"))));
    }

    @Test
    void aCollaboratorCannotUnpublishFromTheWorkspace() throws Exception {
        Playlist shared = published("Morning coffee");
        shared.setOwnerId(9L);
        given(playlistService.viewPublished(7L)).willReturn(shared);
        given(playlistService.publishedSongs(7L)).willReturn(List.of());
        given(playlistService.ownerName(9L)).willReturn("Dana Designer");

        mockMvc.perform(get("/workspace/7").with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Unpublish"))));
    }

    @Test
    void anAdminCanUnpublishAnotherOwnersPlaylistFromTheWorkspace() throws Exception {
        Playlist shared = published("Morning coffee");
        shared.setOwnerId(9L);
        given(playlistService.viewPublished(7L)).willReturn(shared);
        given(playlistService.publishedSongs(7L)).willReturn(List.of());
        given(playlistService.ownerName(9L)).willReturn("Dana Designer");

        mockMvc.perform(get("/workspace/7").with(user(principal(Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Unpublish")));
    }

    @Test
    void aDraftIsNotAvailableInTheWorkspace() throws Exception {
        given(playlistService.viewPublished(9L)).willThrow(new PlaylistNotFoundException(9L));

        mockMvc.perform(get("/workspace/9").with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("This page is not available")));
    }

    @Test
    void unpublishFromTheWorkspaceReturnsToTheList() throws Exception {
        mockMvc.perform(post("/playlists/7/unpublish")
                        .param("returnTo", "/workspace")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(redirectedUrl(Routes.WORKSPACE));

        then(playlistService).should().unpublish(7L, 1L);
    }

    @Test
    void anAdminUnpublishFromTheWorkspaceReturnsToTheList() throws Exception {
        mockMvc.perform(post("/playlists/7/unpublish")
                        .param("returnTo", "/workspace")
                        .with(user(principal(Role.ADMIN)))
                        .with(csrf()))
                .andExpect(redirectedUrl(Routes.WORKSPACE));

        then(playlistService).should().unpublish(7L, 1L);
    }

    private static PublishedPlaylistCard card(String name) {
        return new PublishedPlaylistCard(7L, name, "Dana Designer", 4, LocalDateTime.now(),
                List.of("https://cdn.example/cover.jpg"), List.of("Pop"));
    }

    private static Playlist published(String name) {
        Playlist playlist = new Playlist(name, 1L);
        playlist.setStatus(PlaylistStatus.PUBLISHED);
        playlist.setPublishedAt(LocalDateTime.now());
        playlist.touch(1L);
        ReflectionTestUtils.setField(playlist, "id", 7L);
        return playlist;
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
