package com.funix.swp490x.mrs.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funix.swp490x.mrs.catalog.CatalogImportService;
import com.funix.swp490x.mrs.catalog.SongDraftUploadService;
import com.funix.swp490x.mrs.config.SecurityConfig;
import com.funix.swp490x.mrs.config.WebConfig;
import com.funix.swp490x.mrs.domain.Playlist;
import com.funix.swp490x.mrs.domain.PlaylistStatus;
import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.mail.NotificationService;
import com.funix.swp490x.mrs.mail.SesIdentityService;
import com.funix.swp490x.mrs.repository.TagRepository;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.service.SongCatalogService;
import com.funix.swp490x.mrs.security.LoginAttemptService;
import com.funix.swp490x.mrs.security.LoginFailureHandler;
import com.funix.swp490x.mrs.security.LoginSuccessHandler;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.security.MrsUserDetailsService;
import com.funix.swp490x.mrs.security.PasswordResetTokenService;
import com.funix.swp490x.mrs.service.AuthService;
import com.funix.swp490x.mrs.service.PlaylistService;
import com.funix.swp490x.mrs.service.UserAccountService;
import com.funix.swp490x.mrs.web.admin.AdminCatalogController;
import com.funix.swp490x.mrs.web.admin.AdminController;
import com.funix.swp490x.mrs.web.admin.AdminPlaylistController;
import com.funix.swp490x.mrs.web.admin.AdminUserController;
import com.funix.swp490x.mrs.web.support.ShellModelAdvice;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;

/**
 * Renders every screen through the real Thymeleaf layouts, and checks the
 * role-based access rules of spec 2.1.
 *
 * <p>Runs on the web layer only, so no database is needed.
 */
@WebMvcTest(controllers = {AuthController.class, HomeController.class, SearchController.class,
        SongBrowseController.class, PlaylistController.class, WorkspaceController.class,
        ProfileController.class, AdminController.class, AdminUserController.class,
        AdminPlaylistController.class, AdminCatalogController.class,
        AccountPasswordController.class})
@Import({SecurityConfig.class, WebConfig.class, ShellModelAdvice.class, LoginSuccessHandler.class,
        LoginFailureHandler.class, LoginAttemptService.class, MrsUserDetailsService.class,
        PasswordResetTokenService.class})
class ScreenRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserAccountService userAccountService;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private SesIdentityService sesIdentityService;

    @MockitoBean
    private SongCatalogService songCatalogService;

    @MockitoBean
    private TagRepository tagRepository;

    @MockitoBean
    private CatalogImportService catalogImportService;

    @MockitoBean
    private SongDraftUploadService songDraftUploadService;

    @MockitoBean
    private PlaylistService playlistService;

    @MockitoBean
    private com.funix.swp490x.mrs.catalog.TagSuggestionService tagSuggestionService;

    @MockitoBean
    private com.funix.swp490x.mrs.service.SearchService searchService;

    @BeforeEach
    void listsAreEmptyByDefault() {
        given(userAccountService.search(nullable(Role.class), nullable(UserStatus.class),
                nullable(String.class), anyInt())).willReturn(Page.empty());
        given(playlistService.search(nullable(Long.class), nullable(PlaylistStatus.class),
                nullable(String.class), anyInt())).willReturn(Page.empty());
        given(playlistService.editableDrafts(nullable(Long.class))).willReturn(List.of());
        given(playlistService.view(nullable(Long.class), nullable(Long.class)))
                .willReturn(draftPlaylist());
        given(playlistService.songs(nullable(Long.class), nullable(Long.class)))
                .willReturn(List.of());
        given(playlistService.searchPublished(nullable(Long.class), nullable(String.class), anyInt()))
                .willReturn(Page.empty());
        given(playlistService.publishedOwners()).willReturn(List.of());
        given(playlistService.viewPublished(nullable(Long.class))).willReturn(publishedPlaylist());
        given(playlistService.publishedSongs(nullable(Long.class))).willReturn(List.of());
        given(playlistService.ownerName(nullable(Long.class))).willReturn("Dana Designer");
        given(songCatalogService.search(nullable(List.class), nullable(List.class),
                nullable(List.class), nullable(List.class), nullable(String.class), anyInt()))
                .willReturn(Page.empty());
        given(tagRepository.findAllUsedOrderByTypeAscNameAsc()).willReturn(List.of());
        given(catalogImportService.lastRun()).willReturn(Optional.empty());
        given(catalogImportService.sourceDescription()).willReturn("s3://bucket/song-data/");
        given(songDraftUploadService.registeredProviders())
                .willReturn(List.of("EpidemicSound", "NCS", "OneOff"));
        given(searchService.search(nullable(List.class), nullable(List.class),
                nullable(List.class), nullable(List.class), nullable(String.class),
                nullable(Integer.class), anyInt()))
                .willReturn(Page.empty());
        given(searchService.chips(nullable(List.class), nullable(List.class),
                nullable(List.class), nullable(List.class), nullable(String.class),
                nullable(String.class), nullable(Integer.class)))
                .willReturn(List.of());
    }

    /**
     * A saved-looking Draft. The id and timestamps are only set on persist, and
     * the detail screen formats {@code lastModifiedAt} rather than guarding it.
     */
    private static Playlist draftPlaylist() {
        Playlist playlist = new Playlist("Morning coffee", 1L);
        playlist.touch(1L);
        ReflectionTestUtils.setField(playlist, "id", 5L);
        return playlist;
    }

    private static Playlist publishedPlaylist() {
        Playlist playlist = new Playlist("Launch party", 1L);
        playlist.setStatus(PlaylistStatus.PUBLISHED);
        playlist.setPublishedAt(java.time.LocalDateTime.now());
        playlist.touch(1L);
        ReflectionTestUtils.setField(playlist, "id", 8L);
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

    @Test
    void loginScreenRenders() throws Exception {
        mockMvc.perform(get(Routes.LOGIN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign in to MRS")));
    }

    @Test
    void loginScreenShowsLockoutBanner() throws Exception {
        mockMvc.perform(get(Routes.LOGIN).param("locked", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Too many attempts")));
    }

    /** A banner must never displace the credentials it sits above (spec 4.1). */
    @Test
    void loginScreenKeepsTheCredentialFormBesideABanner() throws Exception {
        mockMvc.perform(get(Routes.LOGIN).param("logout", ""))
                .andExpect(content().string(containsString("You have been signed out")))
                .andExpect(content().string(containsString("name=\"email\"")))
                .andExpect(content().string(containsString("name=\"password\"")))
                .andExpect(content().string(containsString("Sign in")));
    }

    @Test
    void loginScreenHidesBannersWhenThereIsNothingToReport() throws Exception {
        mockMvc.perform(get(Routes.LOGIN))
                .andExpect(content().string(not(containsString("Too many attempts"))));
    }

    @Test
    void passwordResetRequestScreenRenders() throws Exception {
        mockMvc.perform(get(Routes.PASSWORD_RESET))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reset your password")));
    }

    @Test
    void unknownResetTokenRendersTheExpiredCard() throws Exception {
        mockMvc.perform(get(Routes.PASSWORD_RESET_SET).param("token", "not-a-real-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("This link has expired")));
    }

    @ParameterizedTest
    @ValueSource(strings = {Routes.SEARCH, Routes.SONGS, Routes.PLAYLISTS, Routes.PLAYLISTS + "/1",
            Routes.WORKSPACE, Routes.WORKSPACE + "/1", Routes.PROFILE})
    void curationScreensRenderForContentDesigner(String path) throws Exception {
        mockMvc.perform(get(path).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk());
    }

    @Test
    void songsBrowseIsClosedToCustomers() throws Exception {
        mockMvc.perform(get(Routes.SONGS).with(user(principal(Role.CUSTOMER))))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {Routes.ADMIN_USERS, Routes.ADMIN_CATALOG, Routes.ADMIN_PLAYLISTS,
            Routes.ADMIN_SETTINGS, Routes.ADMIN_LOGS})
    void adminScreensRenderForAdmin(String path) throws Exception {
        mockMvc.perform(get(path).with(user(principal(Role.ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    void adminAreaIsClosedToContentDesigners() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_USERS).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCatalogIsClosedToCustomers() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_CATALOG).with(user(principal(Role.CUSTOMER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchIsClosedToCustomers() throws Exception {
        mockMvc.perform(get(Routes.SEARCH).with(user(principal(Role.CUSTOMER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void sidebarOffersSearchToCuratorsOnly() throws Exception {
        mockMvc.perform(get(Routes.WORKSPACE).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(content().string(containsString("/search")));

        mockMvc.perform(get(Routes.WORKSPACE).with(user(principal(Role.CUSTOMER))))
                .andExpect(content().string(not(containsString("/search"))));
    }

    @Test
    void sidebarOffersAllPlaylistsToAdminOnly() throws Exception {
        mockMvc.perform(get(Routes.PROFILE).with(user(principal(Role.ADMIN))))
                .andExpect(content().string(containsString("href=\"/admin/playlists\"")));

        mockMvc.perform(get(Routes.WORKSPACE).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(content().string(not(containsString("/admin/playlists"))));

        mockMvc.perform(get(Routes.ADMIN_PLAYLISTS).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void sidebarOffersMyPlaylistsToCuratorsOnly() throws Exception {
        mockMvc.perform(get(Routes.WORKSPACE).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(content().string(containsString("href=\"/playlists\"")));

        mockMvc.perform(get(Routes.WORKSPACE).with(user(principal(Role.CUSTOMER))))
                .andExpect(content().string(not(containsString("href=\"/playlists\""))));
    }

    @Test
    void sidebarOffersSongsToContentDesignerOnly() throws Exception {
        mockMvc.perform(get(Routes.WORKSPACE).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(content().string(containsString("/songs")));

        mockMvc.perform(get(Routes.WORKSPACE).with(user(principal(Role.CUSTOMER))))
                .andExpect(content().string(not(containsString("/songs"))));

        mockMvc.perform(get(Routes.PROFILE).with(user(principal(Role.ADMIN))))
                .andExpect(content().string(not(containsString("href=\"/songs\""))));
    }

    @Test
    void sidebarShowsTheAdminGroupToAdminsOnly() throws Exception {
        mockMvc.perform(get(Routes.PROFILE).with(user(principal(Role.ADMIN))))
                .andExpect(content().string(containsString("System Settings")));

        mockMvc.perform(get(Routes.PROFILE).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(content().string(not(containsString("System Settings"))));
    }

    @Test
    void rootForwardsEachRoleToItsLandingPage() throws Exception {
        mockMvc.perform(get("/").with(user(principal(Role.ADMIN))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.ADMIN_USERS));

        mockMvc.perform(get("/").with(user(principal(Role.CUSTOMER))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.WORKSPACE));
    }

    @Test
    void accountsOwingAPasswordChangeAreHeldOnTheChangeScreen() throws Exception {
        User user = new User();
        user.setId(2L);
        user.setUsername("Fresh Account");
        user.setEmail("fresh@mrs.local");
        user.setPasswordHash("{noop}irrelevant");
        user.setRole(Role.CONTENT_DESIGNER);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(true);

        mockMvc.perform(get(Routes.PLAYLISTS).with(user(new MrsUserDetails(user, true))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.PASSWORD_CHANGE));
    }
}
