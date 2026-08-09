package com.funix.swp490x.mrs.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funix.swp490x.mrs.catalog.CatalogImportService;
import com.funix.swp490x.mrs.catalog.CatalogImportService.PendingChanges;
import com.funix.swp490x.mrs.catalog.CatalogUploadService;
import com.funix.swp490x.mrs.config.SecurityConfig;
import com.funix.swp490x.mrs.config.WebConfig;
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
import com.funix.swp490x.mrs.service.UserAccountService;
import com.funix.swp490x.mrs.web.admin.AdminCatalogController;
import com.funix.swp490x.mrs.web.admin.AdminController;
import com.funix.swp490x.mrs.web.admin.AdminImportController;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyBoolean;
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
        PlaylistController.class, WorkspaceController.class, ProfileController.class,
        AdminController.class, AdminUserController.class, AdminCatalogController.class,
        AdminImportController.class, AccountPasswordController.class})
@Import({SecurityConfig.class, WebConfig.class, ShellModelAdvice.class, LoginSuccessHandler.class,
        LoginFailureHandler.class, LoginAttemptService.class, MrsUserDetailsService.class,
        PasswordResetTokenService.class})
class ScreenRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

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
    private CatalogUploadService catalogUploadService;

    @BeforeEach
    void listsAreEmptyByDefault() {
        given(userAccountService.search(nullable(Role.class), nullable(UserStatus.class),
                nullable(String.class), anyInt())).willReturn(Page.empty());
        given(songCatalogService.search(nullable(String.class), nullable(Long.class),
                nullable(String.class), anyBoolean(), anyBoolean(), anyInt()))
                .willReturn(Page.empty());
        given(tagRepository.findAllByOrderByTypeAscNameAsc()).willReturn(List.of());
        given(catalogImportService.lastRun()).willReturn(Optional.empty());
        given(catalogImportService.pendingChanges())
                .willReturn(new PendingChanges(0, 0, 0, "s3://bucket/song-data/"));
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
    @ValueSource(strings = {Routes.SEARCH, Routes.PLAYLISTS, Routes.PLAYLISTS + "/1",
            Routes.WORKSPACE, Routes.WORKSPACE + "/1", Routes.PROFILE})
    void curationScreensRenderForContentDesigner(String path) throws Exception {
        mockMvc.perform(get(path).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {Routes.ADMIN_USERS, Routes.ADMIN_CATALOG, Routes.ADMIN_IMPORT,
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
    void sidebarShowsTheAdminGroupToAdminsOnly() throws Exception {
        mockMvc.perform(get(Routes.PROFILE).with(user(principal(Role.ADMIN))))
                .andExpect(content().string(containsString("Catalog Import")));

        mockMvc.perform(get(Routes.PROFILE).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(content().string(not(containsString("Catalog Import"))));
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
