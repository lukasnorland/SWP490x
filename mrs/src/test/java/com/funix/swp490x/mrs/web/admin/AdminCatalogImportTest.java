package com.funix.swp490x.mrs.web.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
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
import com.funix.swp490x.mrs.catalog.CatalogStoreException;
import com.funix.swp490x.mrs.catalog.ImportSummary;
import com.funix.swp490x.mrs.catalog.ImportSummary.SkippedRow;
import com.funix.swp490x.mrs.catalog.InvalidClassificationException;
import com.funix.swp490x.mrs.catalog.SongDraftUploadService;
import com.funix.swp490x.mrs.catalog.SongDraftUploadService.MediaUploadResult;
import com.funix.swp490x.mrs.catalog.TagSuggestionService;
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
import com.funix.swp490x.mrs.service.PlaylistOption;
import com.funix.swp490x.mrs.service.PlaylistService;
import com.funix.swp490x.mrs.service.SongCatalogService;
import com.funix.swp490x.mrs.service.SongCatalogService.SongEdit;
import com.funix.swp490x.mrs.service.SongNotFoundException;
import com.funix.swp490x.mrs.service.StaleSongException;
import com.funix.swp490x.mrs.web.Messages;
import com.funix.swp490x.mrs.web.Routes;
import com.funix.swp490x.mrs.web.support.MultipartUploadAdvice;
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
 * P-06b's song table together with the Add Song and Sync Catalog controls it
 * absorbed from the retired import screen (UC-28, UC-29, spec 4.10).
 */
@WebMvcTest(controllers = AdminCatalogController.class)
@Import({SecurityConfig.class, WebConfig.class, ShellModelAdvice.class, MultipartUploadAdvice.class,
        LoginSuccessHandler.class, LoginFailureHandler.class, LoginAttemptService.class,
        MrsUserDetailsService.class})
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
    private SongDraftUploadService draftUploadService;

    @MockitoBean
    private PlaylistService playlistService;

    @MockitoBean
    private TagSuggestionService tagSuggestionService;

    @BeforeEach
    void defaults() {
        given(songCatalogService.search(nullable(List.class), nullable(List.class),
                nullable(List.class), nullable(List.class), nullable(String.class), anyInt()))
                .willReturn(Page.empty());
        given(songCatalogService.providers()).willReturn(List.of("EpidemicSound"));
        given(tagRepository.findAllUsedOrderByTypeAscNameAsc()).willReturn(List.of());
        given(playlistService.editableDrafts(nullable(Long.class))).willReturn(List.of());
        given(importService.lastRun()).willReturn(Optional.empty());
        given(importService.sourceDescription()).willReturn("s3://bucket/song-data/");
        given(draftUploadService.registeredProviders())
                .willReturn(List.of("EpidemicSound", "NCS", "OneOff"));
        given(tagSuggestionService.suggest(any(), nullable(String.class), anyInt()))
                .willReturn(List.of("Pop", "Dubstep"));
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
                .andExpect(content().string(containsString("Dreamy")))
                .andExpect(content().string(containsString("Female Vocals")))
                .andExpect(content().string(containsString("<th scope=\"col\">Genre</th>")))
                .andExpect(content().string(containsString("<th scope=\"col\">Mood</th>")))
                .andExpect(content().string(containsString("<th scope=\"col\">Tags</th>")))
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
        given(songCatalogService.search(nullable(List.class), nullable(List.class),
                nullable(List.class), nullable(List.class), nullable(String.class), anyInt()))
                .willReturn(new PageImpl<>(List.of(songs), PageRequest.of(0, 20), songs.length));
    }

    @Test
    void catalogPassesEveryFilterThrough() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_CATALOG)
                        .param("provider", "EpidemicSound")
                        .param("genreId", "3")
                        .param("moodId", "4")
                        .param("tagId", "5")
                        .param("q", "ice")
                        .param("page", "2")
                        .with(user(admin())))
                .andExpect(status().isOk());

        then(songCatalogService).should()
                .search(List.of("EpidemicSound"), List.of(3L), List.of(4L), List.of(5L), "ice", 2);
    }

    @Test
    void catalogPassesRepeatedFilterParamsThrough() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_CATALOG)
                        .param("provider", "EpidemicSound")
                        .param("provider", "NCS")
                        .param("genreId", "3")
                        .param("genreId", "7")
                        .param("moodId", "4")
                        .param("tagId", "5")
                        .param("tagId", "9")
                        .with(user(admin())))
                .andExpect(status().isOk());

        then(songCatalogService).should()
                .search(List.of("EpidemicSound", "NCS"), List.of(3L, 7L), List.of(4L),
                        List.of(5L, 9L), null, 0);
    }

    @Test
    void catalogPagerRepeatsMultiValueFilters() throws Exception {
        given(songCatalogService.search(nullable(List.class), nullable(List.class),
                nullable(List.class), nullable(List.class), nullable(String.class), anyInt()))
                .willReturn(new PageImpl<>(List.of(song("Ice Cream", "Sugar Blizz")),
                        PageRequest.of(0, 20), 40));

        mockMvc.perform(get(Routes.ADMIN_CATALOG)
                        .param("genreId", "3")
                        .param("genreId", "7")
                        .header(AdminCatalogController.PARTIAL_RESULTS_HEADER,
                                AdminCatalogController.PARTIAL_RESULTS_VALUE)
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("genreId=3")))
                .andExpect(content().string(containsString("genreId=7")));
    }

    @Test
    void catalogSplitsGenreMoodAndTagFilters() throws Exception {
        given(tagRepository.findAllUsedOrderByTypeAscNameAsc()).willReturn(List.of(
                new Tag(TagType.GENRE, "Pop"),
                new Tag(TagType.GENRE, "Dubstep"),
                new Tag(TagType.MOOD, "Dreamy"),
                new Tag(TagType.MOOD, "Aggressive"),
                new Tag(TagType.TAGS, "Female Vocals")));

        mockMvc.perform(get(Routes.ADMIN_CATALOG).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"filterGenre\"")))
                .andExpect(content().string(containsString("id=\"filterMood\"")))
                .andExpect(content().string(containsString("id=\"filterTag\"")))
                .andExpect(content().string(containsString("name=\"genreId\"")))
                .andExpect(content().string(containsString("name=\"moodId\"")))
                .andExpect(content().string(containsString("type=\"checkbox\"")))
                .andExpect(content().string(containsString(">Pop</span>")))
                .andExpect(content().string(containsString(">Dubstep</span>")))
                .andExpect(content().string(containsString(">Aggressive</span>")))
                .andExpect(content().string(containsString(">Female Vocals</span>")))
                .andExpect(content().string(not(containsString(">2010s</option>"))))
                .andExpect(content().string(not(containsString("Untagged only"))))
                .andExpect(content().string(not(containsString("No preview audio"))))
                .andExpect(content().string(not(containsString("Tag dictionary"))));

        then(tagRepository).should().findAllUsedOrderByTypeAscNameAsc();
    }

    /** DC-03: songs no filtered search can reach are called out, not hidden. */
    @Test
    void catalogWarnsAboutUntaggedSongs() throws Exception {
        given(songCatalogService.untaggedCount()).willReturn(4L);

        mockMvc.perform(get(Routes.ADMIN_CATALOG).with(user(admin())))
                .andExpect(content().string(containsString("untagged")));
    }

    @Test
    void catalogShowsAnEmptyStatePointingAtAddSong() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_CATALOG).with(user(admin())))
                .andExpect(content().string(containsString("No songs match")))
                .andExpect(content().string(containsString("Use Add Song to upload one")));
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
                .andExpect(content().string(not(containsString("Song Catalog"))))
                .andExpect(content().string(not(containsString("sidebar__brand"))));

        then(tagRepository).should(never()).findAllUsedOrderByTypeAscNameAsc();
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
    void catalogShowsTheSyncControlWithoutListingThePrefix() throws Exception {
        given(importService.sourceDescription()).willReturn("s3://mrs-assets/song-data/");

        mockMvc.perform(get(Routes.ADMIN_CATALOG).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("s3://mrs-assets/song-data/")))
                .andExpect(content().string(containsString("Sync Catalog")))
                .andExpect(content().string(containsString("/admin/catalog/sync")))
                .andExpect(content().string(not(containsString("Import 15 song(s)"))));

        then(importService).should(never()).pendingChanges();
    }

    @Test
    void catalogShowsTheLastRun() throws Exception {
        CatalogImportRun run = new CatalogImportRun(ImportTrigger.SCHEDULED, null);
        run.setObjectsListed(3010);
        run.setAdded(2);
        given(importService.lastRun()).willReturn(Optional.of(run));

        mockMvc.perform(get(Routes.ADMIN_CATALOG).with(user(admin())))
                .andExpect(content().string(containsString("Last sync")))
                .andExpect(content().string(containsString("Scheduled")));
    }

    @Test
    void catalogCarriesTheAddSongUploadModal() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_CATALOG).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Add Song")))
                .andExpect(content().string(containsString("Upload audio")))
                .andExpect(content().string(containsString("/admin/catalog/songs")))
                .andExpect(content().string(containsString("data-song-upload")))
                .andExpect(content().string(containsString("data-audio-dropzone")))
                .andExpect(content().string(containsString("songDraftTemplate")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("NCS")))
                .andExpect(content().string(not(containsString("Catalog Import"))))
                .andExpect(content().string(not(containsString("/admin/import"))));
    }

    @Test
    void catalogIncludesTheSyncProgressPanel() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_CATALOG).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"importProgress\"")))
                .andExpect(content().string(containsString("/admin/catalog/sync/status")))
                .andExpect(content().string(containsString("data-import-form")));
    }

    /** FT-06: ADMIN never reaches /songs, so the + has to be here too. */
    @Test
    void catalogOffersTheAddToPlaylistColumn() throws Exception {
        showing(song("Ice Cream", "Sugar Blizz"));
        given(playlistService.editableDrafts(7L))
                .willReturn(List.of(new PlaylistOption(3L, "Morning coffee", 2, 5)));

        mockMvc.perform(get(Routes.ADMIN_CATALOG).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">Add</th>")))
                .andExpect(content().string(containsString("data-add-to-playlist")))
                .andExpect(content().string(containsString("id=\"addToPlaylist\"")))
                .andExpect(content().string(containsString("Morning coffee")));
    }

    /** The results-only swap must not drag the modals along with it. */
    @Test
    void catalogPartialLeavesTheUploadModalOut() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_CATALOG)
                        .header(AdminCatalogController.PARTIAL_RESULTS_HEADER,
                                AdminCatalogController.PARTIAL_RESULTS_VALUE)
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("data-song-upload"))))
                .andExpect(content().string(not(containsString("id=\"importProgress\""))))
                .andExpect(content().string(not(containsString("id=\"addToPlaylist\""))));
    }

    @Test
    void syncStatusReturnsTheCurrentProgress() throws Exception {
        given(importService.progress()).willReturn(new ImportProgress(
                true, "importing", "Importing songs…", 3010, 15, 4, 3, 1, 0, 24));

        mockMvc.perform(get(Routes.ADMIN_CATALOG_SYNC_STATUS).with(user(admin())))
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
    void uploadingMediaAttributesTheRunToTheSignedInAdmin() throws Exception {
        given(draftUploadService.upload(any(), eq(7L)))
                .willReturn(new MediaUploadResult(1, List.of(), null, false));

        MockMultipartFile audio = new MockMultipartFile("drafts[0].audio", "track.mp3",
                "audio/mpeg", new byte[] {1, 2, 3});

        mockMvc.perform(multipart(Routes.ADMIN_CATALOG_SONGS).file(audio)
                        .param("drafts[0].title", "Shine")
                        .param("drafts[0].sourceProvider", "NCS")
                        .with(csrf()).with(user(admin())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.ADMIN_CATALOG))
                .andExpect(flash().attribute("flashVariant", "success"));

        then(draftUploadService).should().upload(any(), eq(7L));
    }

    @Test
    void aStagedMediaUploadWhoseSyncIsAlreadyRunningWarnsTheAdmin() throws Exception {
        given(draftUploadService.upload(any(), eq(7L)))
                .willReturn(new MediaUploadResult(1, List.of(), ImportSummary.refused(), false));

        MockMultipartFile audio = new MockMultipartFile("drafts[0].audio", "track.mp3",
                "audio/mpeg", new byte[] {1, 2, 3});

        mockMvc.perform(multipart(Routes.ADMIN_CATALOG_SONGS).file(audio)
                        .param("drafts[0].title", "Shine")
                        .param("drafts[0].sourceProvider", "NCS")
                        .with(csrf()).with(user(admin())))
                .andExpect(flash().attribute("flash", Messages.UPLOAD_SYNC_SKIPPED));
    }

    /**
     * FT-09 NAC-03: the catalog has no rejection table, so a partly rejected
     * batch has to name the offender in the notice itself.
     */
    @Test
    void aPartlyRejectedUploadNamesTheRejectedSongInTheFlash() throws Exception {
        given(draftUploadService.upload(any(), eq(7L)))
                .willReturn(new MediaUploadResult(1,
                        List.of(new SkippedRow("track.mp3", "missing title")), null, false));

        MockMultipartFile audio = new MockMultipartFile("drafts[0].audio", "track.mp3",
                "audio/mpeg", new byte[] {1, 2, 3});

        mockMvc.perform(multipart(Routes.ADMIN_CATALOG_SONGS).file(audio)
                        .param("drafts[0].title", "Shine")
                        .param("drafts[0].sourceProvider", "NCS")
                        .with(csrf()).with(user(admin())))
                .andExpect(flash().attribute("flashVariant", "warning"))
                .andExpect(flash().attribute("flash",
                        containsString("track.mp3 — missing title")));
    }

    @Test
    void anAjaxMediaUploadReturnsJsonSoTheFormCanShowErrors() throws Exception {
        given(draftUploadService.upload(any(), any()))
                .willReturn(new MediaUploadResult(0,
                        List.of(new SkippedRow("Shine", "missing title")), null, false));

        MockMultipartFile audio = new MockMultipartFile("drafts[0].audio", "track.mp3",
                "audio/mpeg", new byte[] {1, 2, 3});

        mockMvc.perform(multipart(Routes.ADMIN_CATALOG_SONGS).file(audio)
                        .param("drafts[0].title", "Shine")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .with(csrf()).with(user(admin())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(Messages.MEDIA_UPLOAD_ALL_REJECTED))
                .andExpect(jsonPath("$.rejected[0].reason").value("missing title"));
    }

    @Test
    void anAjaxMediaUploadSendsTheFormBackToTheCatalog() throws Exception {
        given(draftUploadService.upload(any(), any()))
                .willReturn(new MediaUploadResult(1, List.of(), null, false));

        MockMultipartFile audio = new MockMultipartFile("drafts[0].audio", "track.mp3",
                "audio/mpeg", new byte[] {1, 2, 3});

        mockMvc.perform(multipart(Routes.ADMIN_CATALOG_SONGS).file(audio)
                        .param("drafts[0].title", "Shine")
                        .param("drafts[0].sourceProvider", "NCS")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .with(csrf()).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploaded").value(1))
                .andExpect(jsonPath("$.redirect").value(Routes.ADMIN_CATALOG));
    }

    @Test
    void contentDesignersCannotUploadMedia() throws Exception {
        MockMultipartFile audio = new MockMultipartFile("drafts[0].audio", "track.mp3",
                "audio/mpeg", new byte[] {1});

        mockMvc.perform(multipart(Routes.ADMIN_CATALOG_SONGS).file(audio)
                        .with(csrf()).with(user(designer())))
                .andExpect(status().isForbidden());

        then(draftUploadService).should(never()).upload(any(), any());
    }

    @Test
    void aMediaUploadCannotBeTriggeredWithoutACsrfToken() throws Exception {
        MockMultipartFile audio = new MockMultipartFile("drafts[0].audio", "track.mp3",
                "audio/mpeg", new byte[] {1});

        mockMvc.perform(multipart(Routes.ADMIN_CATALOG_SONGS).file(audio).with(user(admin())))
                .andExpect(status().is3xxRedirection());

        then(draftUploadService).should(never()).upload(any(), any());
    }

    @Test
    void syncingTheCatalogStartsTheImportInTheBackground() throws Exception {
        given(importService.startAsync(eq(ImportTrigger.MANUAL), anyLong(), anyBoolean()))
                .willReturn(true);

        mockMvc.perform(post(Routes.ADMIN_CATALOG_SYNC).with(csrf()).with(user(admin())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.ADMIN_CATALOG))
                .andExpect(flash().attribute("flashVariant", "info"))
                .andExpect(flash().attribute("flash", Messages.IMPORT_STARTED));

        then(importService).should().startAsync(ImportTrigger.MANUAL, 7L, false);
        then(importService).should(never()).sync(any(), any(), anyBoolean());
    }

    @Test
    void aRefusedRunSaysAnImportIsAlreadyGoing() throws Exception {
        given(importService.startAsync(any(), any(), anyBoolean())).willReturn(false);

        mockMvc.perform(post(Routes.ADMIN_CATALOG_SYNC).with(csrf()).with(user(admin())))
                .andExpect(flash().attribute("flash", Messages.IMPORT_ALREADY_RUNNING));
    }

    /** Spec 2.1: the whole admin area is ADMIN-only. */
    @Test
    void contentDesignersCannotReachTheCatalogOrRunAnImport() throws Exception {
        MrsUserDetails designer = designer();

        mockMvc.perform(get(Routes.ADMIN_CATALOG).with(user(designer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(Routes.ADMIN_CATALOG_SYNC).with(csrf()).with(user(designer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/catalog/12")
                        .param("title", "Ice Cream")
                        .param("version", "0")
                        .with(csrf())
                        .with(user(designer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/catalog/12/delete").with(csrf()).with(user(designer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(Routes.ADMIN_CATALOG_SYNC_STATUS).with(user(designer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(Routes.ADMIN_CATALOG_TAG_SUGGEST)
                        .param("type", "GENRE")
                        .param("q", "dub")
                        .with(user(designer)))
                .andExpect(status().isForbidden());

        then(importService).should(never()).sync(any(), any(), anyBoolean());
        then(importService).should(never()).startAsync(any(), any(), anyBoolean());
        then(songCatalogService).should(never()).update(any(), anyInt(), any(), any());
        then(songCatalogService).should(never()).delete(any(), any());
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
        mockMvc.perform(post(Routes.ADMIN_CATALOG_SYNC).with(user(admin())))
                .andExpect(status().is3xxRedirection());

        then(importService).should(never()).sync(any(), any(), anyBoolean());
        then(importService).should(never()).startAsync(any(), any(), anyBoolean());
    }

    @Test
    void catalogOffersPerRowEditAndDelete() throws Exception {
        showing(song("Ice Cream", "Sugar Blizz"));

        mockMvc.perform(get(Routes.ADMIN_CATALOG).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit song")))
                .andExpect(content().string(containsString("data-edit-song")))
                .andExpect(content().string(containsString("/admin/catalog/12/delete")))
                .andExpect(content().string(containsString("cannot be changed here")))
                .andExpect(content().string(not(containsString("name=\"title\""))))
                .andExpect(content().string(not(containsString("name=\"artist\""))))
                .andExpect(content().string(not(containsString("name=\"isrc\""))));
    }

    @Test
    void catalogSaveReportsAStagingFailure() throws Exception {
        willThrow(new CatalogStoreException("denied")).given(songCatalogService)
                .update(eq(12L), eq(3), any(SongEdit.class), any());

        mockMvc.perform(post("/admin/catalog/12")
                        .param("explicit", "true")
                        .param("version", "3")
                        .with(csrf())
                        .with(user(admin())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.ADMIN_CATALOG))
                .andExpect(flash().attribute("flash", Messages.SONG_SAVE_FAILED));
    }

    @Test
    void catalogSaveRedirectsOnSuccess() throws Exception {
        mockMvc.perform(post("/admin/catalog/12")
                        .param("explicit", "true")
                        .param("genres", "Pop")
                        .param("version", "3")
                        .with(csrf())
                        .with(user(admin())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.ADMIN_CATALOG))
                .andExpect(flash().attribute("flash", Messages.SONG_SAVED));

        then(songCatalogService).should().update(eq(12L), eq(3), any(SongEdit.class), eq(7L));
    }

    @Test
    void catalogSaveReopensTheModalWhenAGenreIsUnknown() throws Exception {
        willThrow(new InvalidClassificationException(List.of("Cinematic"), List.of()))
                .given(songCatalogService).update(eq(12L), eq(3), any(SongEdit.class), any());

        mockMvc.perform(post("/admin/catalog/12")
                        .param("genres", "Cinematic")
                        .param("version", "3")
                        .with(csrf())
                        .with(user(admin())))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Cinematic")))
                .andExpect(content().string(containsString("data-modal-autoshow=\"true\"")));
    }

    @Test
    void catalogSuggestReturnsMusicBrainzNames() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_CATALOG_TAG_SUGGEST)
                        .param("type", "GENRE")
                        .param("q", "dub")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0]").value("Pop"));
    }

    @Test
    void catalogSaveReturns409WhenTheVersionIsStale() throws Exception {
        willThrow(new StaleSongException(12L)).given(songCatalogService)
                .update(eq(12L), eq(0), any(SongEdit.class), any());

        mockMvc.perform(post("/admin/catalog/12")
                        .param("title", "Ice Cream")
                        .param("version", "0")
                        .with(csrf())
                        .with(user(admin())))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString(Messages.SONG_STALE)))
                .andExpect(content().string(not(containsString("data-modal-autoshow=\"true\""))));
    }

    @Test
    void catalogSaveReturns404WhenTheSongIsGone() throws Exception {
        willThrow(new SongNotFoundException(12L)).given(songCatalogService)
                .update(eq(12L), eq(0), any(SongEdit.class), any());

        mockMvc.perform(post("/admin/catalog/12")
                        .param("title", "Ice Cream")
                        .param("version", "0")
                        .with(csrf())
                        .with(user(admin())))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString(Messages.SONG_NOT_FOUND)));
    }

    @Test
    void catalogDeleteRedirectsOnSuccess() throws Exception {
        mockMvc.perform(post("/admin/catalog/12/delete")
                        .with(csrf())
                        .with(user(admin())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.ADMIN_CATALOG))
                .andExpect(flash().attribute("flash", Messages.SONG_DELETED));

        then(songCatalogService).should().delete(12L, 7L);
    }

    @Test
    void catalogDeleteReportsAStagingFailure() throws Exception {
        willThrow(new CatalogStoreException("denied")).given(songCatalogService).delete(12L, 7L);

        mockMvc.perform(post("/admin/catalog/12/delete")
                        .with(csrf())
                        .with(user(admin())))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flash", Messages.SONG_DELETE_FAILED));
    }

    @Test
    void aSongCannotBeEditedOrDeletedWithoutACsrfToken() throws Exception {
        mockMvc.perform(post("/admin/catalog/12")
                        .param("title", "Ice Cream")
                        .param("version", "0")
                        .with(user(admin())))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/admin/catalog/12/delete").with(user(admin())))
                .andExpect(status().is3xxRedirection());

        then(songCatalogService).should(never()).update(any(), anyInt(), any(), any());
        then(songCatalogService).should(never()).delete(any(), any());
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
        song.setId(12L);
        song.setTitle(title);
        song.setArtist(artist);
        song.setSourceProvider("EpidemicSound");
        song.setExternalSourceId("003c5571-5014-387b-978c-2836125178a4");
        song.setDuration(213);
        song.setBpm(110);
        song.setCoverUrl("https://cdn.epidemicsound.com/cover.jpg");
        song.setAudioUrl("https://audiocdn.epidemicsound.com/preview.mp3");
        song.setTags(Set.of(
                new Tag(TagType.GENRE, "Pop"),
                new Tag(TagType.MOOD, "Dreamy"),
                new Tag(TagType.TAGS, "Female Vocals")));
        return song;
    }
}
