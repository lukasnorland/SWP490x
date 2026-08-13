package com.funix.swp490x.mrs.web.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funix.swp490x.mrs.catalog.CatalogImportService;
import com.funix.swp490x.mrs.catalog.CatalogImportService.ImportProgress;
import com.funix.swp490x.mrs.catalog.CatalogImportService.PendingChanges;
import com.funix.swp490x.mrs.catalog.CatalogStoreException;
import com.funix.swp490x.mrs.catalog.CatalogUploadService;
import com.funix.swp490x.mrs.catalog.CatalogUploadService.UploadResult;
import com.funix.swp490x.mrs.catalog.ImportSummary;
import com.funix.swp490x.mrs.catalog.ImportSummary.SkippedRow;
import com.funix.swp490x.mrs.config.SecurityConfig;
import com.funix.swp490x.mrs.config.WebConfig;
import com.funix.swp490x.mrs.domain.CatalogImportRun;
import com.funix.swp490x.mrs.domain.ImportTrigger;
import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.Song;
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.repository.TagRepository;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.LoginAttemptService;
import com.funix.swp490x.mrs.security.LoginFailureHandler;
import com.funix.swp490x.mrs.security.LoginSuccessHandler;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.security.MrsUserDetailsService;
import com.funix.swp490x.mrs.service.SongCatalogService;
import com.funix.swp490x.mrs.web.Messages;
import com.funix.swp490x.mrs.web.Routes;
import com.funix.swp490x.mrs.web.support.ShellModelAdvice;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * P-06b's song table and P-06c's import control (UC-28, spec 4.10 and 4.11).
 */
@WebMvcTest(controllers = {AdminCatalogController.class, AdminImportController.class})
@Import({SecurityConfig.class, WebConfig.class, ShellModelAdvice.class, LoginSuccessHandler.class,
        LoginFailureHandler.class, LoginAttemptService.class, MrsUserDetailsService.class})
class AdminCatalogImportTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SongCatalogService songCatalogService;

    @MockitoBean
    private TagRepository tagRepository;

    @MockitoBean
    private CatalogImportService importService;

    @MockitoBean
    private CatalogUploadService uploadService;

    @BeforeEach
    void defaults() {
        given(songCatalogService.search(nullable(String.class), nullable(Long.class),
                nullable(String.class), anyBoolean(), anyBoolean(), anyInt()))
                .willReturn(Page.empty());
        given(songCatalogService.providers()).willReturn(List.of("EpidemicSound"));
        given(tagRepository.findAllByOrderByTypeAscNameAsc()).willReturn(List.of());
        given(importService.lastRun()).willReturn(Optional.empty());
        given(importService.pendingChanges())
                .willReturn(new PendingChanges(0, 0, 0, "s3://bucket/song-data/"));
    }

    private static MrsUserDetails admin() {
        User user = new User();
        user.setId(7L);
        user.setUsername("Test ADMIN");
        user.setEmail("admin@mrs.local");
        user.setPasswordHash("{noop}irrelevant");
        user.setRole(Role.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(false);
        return new MrsUserDetails(user, true);
    }

    @Test
    void catalogListsTheSongsWithTheirTags() throws Exception {
        showing(song("Ice Cream", "Sugar Blizz"));

        mockMvc.perform(get(Routes.ADMIN_CATALOG).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ice Cream")))
                .andExpect(content().string(containsString("Sugar Blizz")))
                .andExpect(content().string(containsString("Pop")))
                .andExpect(content().string(containsString("EpidemicSound")))
                .andExpect(content().string(
                        containsString("003c5571-5014-387b-978c-2836125178a4")))
                .andExpect(content().string(containsString("cover.jpg")))
                .andExpect(content().string(containsString("110")));
    }

    /**
     * Seconds are shown as m:ss, so the padding matters: 213 is 3:33 and 65 is
     * 1:05, never 1:5.
     */
    @Test
    void catalogFormatsDurationAsMinutesAndPaddedSeconds() throws Exception {
        Song song = song("Ice Cream", "Sugar Blizz");
        song.setDuration(213);
        showing(song);
        mockMvc.perform(get(Routes.ADMIN_CATALOG).with(user(admin())))
                .andExpect(content().string(containsString("3:33")));

        song.setDuration(65);
        showing(song);
        mockMvc.perform(get(Routes.ADMIN_CATALOG).with(user(admin())))
                .andExpect(content().string(containsString("1:05")));
    }

    /** Provider metadata is optional, so a sparse row must still render. */
    @Test
    void catalogRendersASongWithNoArtistCoverDurationOrBpm() throws Exception {
        Song sparse = new Song();
        sparse.setTitle("Bare Minimum");
        sparse.setSourceProvider("DemoProvider");
        sparse.setExternalSourceId("DP-0001");
        showing(sparse);

        mockMvc.perform(get(Routes.ADMIN_CATALOG).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bare Minimum")))
                .andExpect(content().string(containsString("Untagged")));
    }

    private void showing(Song... songs) {
        given(songCatalogService.search(nullable(String.class), nullable(Long.class),
                nullable(String.class), anyBoolean(), anyBoolean(), anyInt()))
                .willReturn(new PageImpl<>(List.of(songs), PageRequest.of(0, 20), songs.length));
    }

    @Test
    void catalogPassesEveryFilterThrough() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_CATALOG)
                        .param("provider", "EpidemicSound")
                        .param("tagId", "5")
                        .param("q", "ice")
                        .param("untagged", "true")
                        .param("noPreview", "true")
                        .param("page", "2")
                        .with(user(admin())))
                .andExpect(status().isOk());

        then(songCatalogService).should()
                .search("EpidemicSound", 5L, "ice", true, true, 2);
    }

    /** DC-03: songs no filtered search can reach are called out, not hidden. */
    @Test
    void catalogWarnsAboutUntaggedSongs() throws Exception {
        given(songCatalogService.untaggedCount()).willReturn(4L);

        mockMvc.perform(get(Routes.ADMIN_CATALOG).with(user(admin())))
                .andExpect(content().string(containsString("untagged")));
    }

    @Test
    void catalogShowsAnEmptyStatePointingAtTheImport() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_CATALOG).with(user(admin())))
                .andExpect(content().string(containsString("No songs match")))
                .andExpect(content().string(containsString("/admin/import")));
    }

    @Test
    void catalogPartialReturnsResultsFragmentWithoutShell() throws Exception {
        showing(song("Ice Cream", "Sugar Blizz"));

        mockMvc.perform(get(Routes.ADMIN_CATALOG)
                        .header(AdminCatalogController.PARTIAL_RESULTS_HEADER,
                                AdminCatalogController.PARTIAL_RESULTS_VALUE)
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("catalog-results")))
                .andExpect(content().string(containsString("Ice Cream")))
                .andExpect(content().string(containsString("Sugar Blizz")))
                .andExpect(content().string(not(containsString("data-preview-bar"))))
                .andExpect(content().string(not(containsString("Song Catalog &amp; Metadata"))))
                .andExpect(content().string(not(containsString("sidebar__brand"))));
    }

    @Test
    void catalogFullPageStillRendersShellChrome() throws Exception {
        showing(song("Ice Cream", "Sugar Blizz"));

        mockMvc.perform(get(Routes.ADMIN_CATALOG).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-preview-bar")))
                .andExpect(content().string(containsString("catalog-results")))
                .andExpect(content().string(containsString("Ice Cream")));
    }

    @Test
    void importScreenShowsWhatARunWouldChange() throws Exception {
        given(importService.pendingChanges())
                .willReturn(new PendingChanges(3010, 12, 3, "s3://mrs-assets/song-data/"));

        mockMvc.perform(get(Routes.ADMIN_IMPORT).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("s3://mrs-assets/song-data/")))
                .andExpect(content().string(containsString("3010")))
                .andExpect(content().string(containsString("Import 15 song(s)")));
    }

    /** An unreachable bucket must not take the screen down with it. */
    @Test
    void importScreenStillRendersWhenTheStoreCannotBeListed() throws Exception {
        given(importService.pendingChanges())
                .willThrow(new CatalogStoreException("no credentials"));

        mockMvc.perform(get(Routes.ADMIN_IMPORT).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("could not be listed")));
    }

    @Test
    void importScreenShowsTheLastRun() throws Exception {
        CatalogImportRun run = new CatalogImportRun(ImportTrigger.SCHEDULED, null);
        run.setObjectsListed(3010);
        run.setAdded(2);
        given(importService.lastRun()).willReturn(Optional.of(run));

        mockMvc.perform(get(Routes.ADMIN_IMPORT).with(user(admin())))
                .andExpect(content().string(containsString("Last run")))
                .andExpect(content().string(containsString("Scheduled")));
    }

    @Test
    void importScreenShowsTheUploadForm() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_IMPORT).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Upload song JSON")))
                .andExpect(content().string(containsString("name=\"files\"")))
                .andExpect(content().string(containsString("/admin/import/upload")));
    }

    @Test
    void importScreenIncludesTheProgressPanel() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_IMPORT).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"importProgress\"")))
                .andExpect(content().string(containsString("/admin/import/status")))
                .andExpect(content().string(containsString("data-import-form")));
    }

    @Test
    void importStatusReturnsTheCurrentProgress() throws Exception {
        given(importService.progress()).willReturn(new ImportProgress(
                true, "importing", "Importing songs…", 3010, 15, 4, 3, 1, 0, 24));

        mockMvc.perform(get(Routes.ADMIN_IMPORT_STATUS).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.phase").value("importing"))
                .andExpect(jsonPath("$.detail").value("Importing songs…"))
                .andExpect(jsonPath("$.listed").value(3010))
                .andExpect(jsonPath("$.toRead").value(15))
                .andExpect(jsonPath("$.processed").value(4))
                .andExpect(jsonPath("$.added").value(3))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.percent").value(24));
    }

    @Test
    void uploadingJsonAttributesTheRunToTheSignedInAdmin() throws Exception {
        given(uploadService.upload(anyList(), eq(7L)))
                .willReturn(new UploadResult(1, List.of(), null, false));

        MockMultipartFile file = new MockMultipartFile("files", "song.json",
                "application/json", "{\"title\":\"T\"}".getBytes());

        mockMvc.perform(multipart(Routes.ADMIN_IMPORT_UPLOAD).file(file)
                        .with(csrf()).with(user(admin())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.ADMIN_IMPORT))
                .andExpect(flash().attribute("flashVariant", "success"))
                .andExpect(flash().attribute("flash",
                        "1 file(s) staged. Import started — this page will show progress."));

        then(uploadService).should().upload(anyList(), eq(7L));
    }

    @Test
    void anEmptyUploadSelectionIsReported() throws Exception {
        given(uploadService.upload(any(), any()))
                .willReturn(UploadResult.emptySelection());

        mockMvc.perform(multipart(Routes.ADMIN_IMPORT_UPLOAD)
                        .with(csrf()).with(user(admin())))
                .andExpect(flash().attribute("flash", Messages.UPLOAD_EMPTY));
    }

    @Test
    void allRejectedUploadsAreListedInFlash() throws Exception {
        given(uploadService.upload(anyList(), anyLong()))
                .willReturn(new UploadResult(0,
                        List.of(new SkippedRow("bad.json", "missing title")), null, false));

        MockMultipartFile file = new MockMultipartFile("files", "bad.json",
                "application/json", "{}".getBytes());

        mockMvc.perform(multipart(Routes.ADMIN_IMPORT_UPLOAD).file(file)
                        .with(csrf()).with(user(admin())))
                .andExpect(flash().attribute("flash", Messages.UPLOAD_ALL_REJECTED))
                .andExpect(flash().attributeExists("uploadRejected"));
    }

    @Test
    void aStagedUploadWhoseSyncIsAlreadyRunningWarnsTheAdmin() throws Exception {
        given(uploadService.upload(anyList(), anyLong()))
                .willReturn(new UploadResult(2, List.of(), ImportSummary.refused(), false));

        MockMultipartFile file = new MockMultipartFile("files", "song.json",
                "application/json", "{}".getBytes());

        mockMvc.perform(multipart(Routes.ADMIN_IMPORT_UPLOAD).file(file)
                        .with(csrf()).with(user(admin())))
                .andExpect(flash().attribute("flash", Messages.UPLOAD_SYNC_SKIPPED));
    }

    @Test
    void contentDesignersCannotUploadJson() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "song.json",
                "application/json", "{}".getBytes());

        mockMvc.perform(multipart(Routes.ADMIN_IMPORT_UPLOAD).file(file)
                        .with(csrf()).with(user(designer())))
                .andExpect(status().isForbidden());

        then(uploadService).should(never()).upload(any(), any());
    }

    @Test
    void anUploadCannotBeTriggeredWithoutACsrfToken() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "song.json",
                "application/json", "{}".getBytes());

        mockMvc.perform(multipart(Routes.ADMIN_IMPORT_UPLOAD).file(file).with(user(admin())))
                .andExpect(status().is3xxRedirection());

        then(uploadService).should(never()).upload(any(), any());
    }

    @Test
    void runningTheImportStartsItInTheBackground() throws Exception {
        given(importService.startAsync(eq(ImportTrigger.MANUAL), anyLong(), anyBoolean()))
                .willReturn(true);

        mockMvc.perform(post(Routes.ADMIN_IMPORT_RUN).with(csrf()).with(user(admin())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.ADMIN_IMPORT))
                .andExpect(flash().attribute("flashVariant", "info"))
                .andExpect(flash().attribute("flash", Messages.IMPORT_STARTED));

        then(importService).should().startAsync(ImportTrigger.MANUAL, 7L, false);
        then(importService).should(never()).sync(any(), any(), anyBoolean());
    }

    @Test
    void theForceFlagIsPassedOnWhenAskedFor() throws Exception {
        given(importService.startAsync(any(), any(), anyBoolean())).willReturn(true);

        mockMvc.perform(post(Routes.ADMIN_IMPORT_RUN).param("force", "true")
                        .with(csrf()).with(user(admin())))
                .andExpect(status().is3xxRedirection());

        then(importService).should().startAsync(ImportTrigger.MANUAL, 7L, true);
    }

    @Test
    void aRefusedRunSaysAnImportIsAlreadyGoing() throws Exception {
        given(importService.startAsync(any(), any(), anyBoolean())).willReturn(false);

        mockMvc.perform(post(Routes.ADMIN_IMPORT_RUN).with(csrf()).with(user(admin())))
                .andExpect(flash().attribute("flash", Messages.IMPORT_ALREADY_RUNNING));
    }

    /** Spec 2.1: the whole admin area is ADMIN-only. */
    @Test
    void contentDesignersCannotReachTheCatalogOrRunAnImport() throws Exception {
        MrsUserDetails designer = designer();

        mockMvc.perform(get(Routes.ADMIN_CATALOG).with(user(designer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(Routes.ADMIN_IMPORT).with(user(designer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(Routes.ADMIN_IMPORT_RUN).with(csrf()).with(user(designer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(Routes.ADMIN_IMPORT_STATUS).with(user(designer)))
                .andExpect(status().isForbidden());

        then(importService).should(never()).sync(any(), any(), anyBoolean());
        then(importService).should(never()).startAsync(any(), any(), anyBoolean());
    }

    @Test
    void customersCannotReachTheAdminCatalog() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_CATALOG).with(user(customer())))
                .andExpect(status().isForbidden());
    }

    /**
     * CSRF is on by default, so a cross-site form cannot start an import. The
     * request is bounced back to login rather than answered with 403 because
     * {@code invalidSessionUrl} handles a request that carries no valid session.
     */
    @Test
    void anImportCannotBeTriggeredWithoutACsrfToken() throws Exception {
        mockMvc.perform(post(Routes.ADMIN_IMPORT_RUN).with(user(admin())))
                .andExpect(status().is3xxRedirection());

        then(importService).should(never()).sync(any(), any(), anyBoolean());
        then(importService).should(never()).startAsync(any(), any(), anyBoolean());
    }

    private static MrsUserDetails designer() {
        User user = new User();
        user.setId(8L);
        user.setUsername("Test CD");
        user.setEmail("cd@mrs.local");
        user.setPasswordHash("{noop}irrelevant");
        user.setRole(Role.CONTENT_DESIGNER);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(false);
        return new MrsUserDetails(user, true);
    }

    private static MrsUserDetails customer() {
        User user = new User();
        user.setId(9L);
        user.setUsername("Test Customer");
        user.setEmail("customer@mrs.local");
        user.setPasswordHash("{noop}irrelevant");
        user.setRole(Role.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(false);
        return new MrsUserDetails(user, true);
    }

    private static Song song(String title, String artist) {
        Song song = new Song();
        song.setTitle(title);
        song.setArtist(artist);
        song.setSourceProvider("EpidemicSound");
        song.setExternalSourceId("003c5571-5014-387b-978c-2836125178a4");
        song.setDuration(213);
        song.setBpm(110);
        song.setCoverUrl("https://cdn.epidemicsound.com/cover.jpg");
        song.setAudioUrl("https://audiocdn.epidemicsound.com/preview.mp3");
        song.setTags(Set.of(new Tag(TagType.GENRE, "Pop")));
        return song;
    }
}
