package com.funix.swp490x.mrs.web.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funix.swp490x.mrs.config.SecurityConfig;
import com.funix.swp490x.mrs.config.WebConfig;
import com.funix.swp490x.mrs.domain.Playlist;
import com.funix.swp490x.mrs.domain.PlaylistStatus;
import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.LoginAttemptService;
import com.funix.swp490x.mrs.security.LoginFailureHandler;
import com.funix.swp490x.mrs.security.LoginSuccessHandler;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.security.MrsUserDetailsService;
import com.funix.swp490x.mrs.service.PlaylistOwner;
import com.funix.swp490x.mrs.service.PlaylistService;
import com.funix.swp490x.mrs.service.PlaylistSummary;
import com.funix.swp490x.mrs.web.Routes;
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

/** P-06f All Playlists: ADMIN sees everything, read-only; nobody else gets in. */
@WebMvcTest(controllers = AdminPlaylistController.class)
@Import({SecurityConfig.class, WebConfig.class, ShellModelAdvice.class, LoginSuccessHandler.class,
        LoginFailureHandler.class, LoginAttemptService.class, MrsUserDetailsService.class})
class AdminPlaylistBrowseTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PlaylistService playlistService;

    @BeforeEach
    void defaults() {
        given(playlistService.searchAll(nullable(Long.class), nullable(PlaylistStatus.class),
                nullable(String.class), anyInt())).willReturn(Page.empty());
        given(playlistService.playlistOwners()).willReturn(List.of(
                new PlaylistOwner(2L, "Dana Designer"), new PlaylistOwner(3L, "Nina Designer")));
    }

    @Test
    void theListShowsEveryOwnersDraftsAndPublishedPlaylists() throws Exception {
        given(playlistService.searchAll(nullable(Long.class), nullable(PlaylistStatus.class),
                nullable(String.class), anyInt()))
                .willReturn(new PageImpl<>(List.of(
                        summary(7L, "Morning coffee", PlaylistStatus.DRAFT, "Dana Designer"),
                        summary(8L, "Launch party", PlaylistStatus.PUBLISHED, "Nina Designer")),
                        PageRequest.of(0, 20), 2));

        mockMvc.perform(get(Routes.ADMIN_PLAYLISTS).with(user(principal(Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Morning coffee")))
                .andExpect(content().string(containsString("Launch party")))
                .andExpect(content().string(containsString("Dana Designer")))
                .andExpect(content().string(containsString("Nina Designer")))
                .andExpect(content().string(containsString("/admin/playlists/7")))
                // Read-only: none of the P-03a row actions.
                .andExpect(content().string(not(containsString("New playlist"))))
                .andExpect(content().string(not(containsString("/playlists/7/delete"))))
                .andExpect(content().string(not(containsString("Rename playlist"))));
    }

    @Test
    void theOwnerStatusAndNameFiltersReachTheService() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_PLAYLISTS)
                        .param("ownerId", "3")
                        .param("status", "DRAFT")
                        .param("q", "coffee")
                        .param("page", "1")
                        .with(user(principal(Role.ADMIN))))
                .andExpect(status().isOk());

        then(playlistService).should().searchAll(3L, PlaylistStatus.DRAFT, "coffee", 1);
    }

    @Test
    void theDetailIsRenderedWithoutAnyEditorOrExportControl() throws Exception {
        Playlist draft = new Playlist("Morning coffee", 2L);
        ReflectionTestUtils.setField(draft, "id", 7L);
        given(playlistService.inspect(7L)).willReturn(draft);
        given(playlistService.inspectSongs(7L)).willReturn(List.of());
        given(playlistService.ownerName(2L)).willReturn("Dana Designer");

        mockMvc.perform(get("/admin/playlists/7").with(user(principal(Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Morning coffee")))
                .andExpect(content().string(containsString("Dana Designer")))
                .andExpect(content().string(containsString("Viewing as administrator")))
                .andExpect(content().string(not(containsString("/playlists/7/publish"))))
                .andExpect(content().string(not(containsString("/playlists/7/delete"))))
                .andExpect(content().string(not(containsString("/playlists/7/export.csv"))))
                .andExpect(content().string(not(containsString("Rename playlist"))));
    }

    @Test
    void theScreenIsClosedToContentDesignersAndCustomers() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_PLAYLISTS).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/playlists/7").with(user(principal(Role.CUSTOMER))))
                .andExpect(status().isForbidden());
    }

    private static PlaylistSummary summary(Long id, String name, PlaylistStatus status,
            String ownerName) {
        return new PlaylistSummary(id, name, status, 3, 1, LocalDateTime.now(), ownerName,
                0, false, ownerName);
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
