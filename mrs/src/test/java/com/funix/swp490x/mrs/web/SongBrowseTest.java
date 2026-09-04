package com.funix.swp490x.mrs.web;

import com.funix.swp490x.mrs.web.Messages;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funix.swp490x.mrs.config.SecurityConfig;
import com.funix.swp490x.mrs.config.WebConfig;
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
import com.funix.swp490x.mrs.service.SongCatalogService.PreviewTrack;
import com.funix.swp490x.mrs.web.support.ShellModelAdvice;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * /songs browse for Content Designer (and ADMIN) — same catalog list and preview
 * wiring as P-06b, without admin import or metadata ops. Closed to Customers.
 */
@WebMvcTest(controllers = SongBrowseController.class)
@Import({SecurityConfig.class, WebConfig.class, ShellModelAdvice.class, LoginSuccessHandler.class,
        LoginFailureHandler.class, LoginAttemptService.class, MrsUserDetailsService.class})
class SongBrowseTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SongCatalogService songCatalogService;

    @MockitoBean
    private TagRepository tagRepository;

    @MockitoBean
    private PlaylistService playlistService;

    @BeforeEach
    void defaults() {
        given(songCatalogService.search(nullable(List.class), nullable(List.class),
                nullable(List.class), nullable(List.class), nullable(String.class), anyInt()))
                .willReturn(Page.empty());
        given(songCatalogService.providers()).willReturn(List.of("EpidemicSound"));
        given(songCatalogService.total()).willReturn(0L);
        given(tagRepository.findAllUsedOrderByTypeAscNameAsc()).willReturn(List.of());
        given(playlistService.editableDrafts(nullable(Long.class))).willReturn(List.of());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"CONTENT_DESIGNER"})
    void songsPageRendersForContentDesigner(Role role) throws Exception {
        showing(song("Ice Cream", "Sugar Blizz"));

        mockMvc.perform(get(Routes.SONGS).with(user(principal(role))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ice Cream")))
                .andExpect(content().string(containsString("Sugar Blizz")))
                .andExpect(content().string(containsString("data-preview-bar")))
                .andExpect(content().string(containsString("data-preview-shuffle")))
                .andExpect(content().string(containsString("data-preview-prev")))
                .andExpect(content().string(containsString("data-preview-next")))
                .andExpect(content().string(containsString("data-preview-repeat")))
                .andExpect(content().string(not(containsString("Untagged only"))));
    }

    /** "Create and add" from a song row redirects back here with its outcome. */
    @Test
    void songsShowsTheFlashItWasRedirectedBackWith() throws Exception {
        mockMvc.perform(get(Routes.SONGS)
                        .flashAttr("flash", Messages.PLAYLIST_CREATED_WITH_SONG)
                        .flashAttr("flashVariant", "success")
                        .with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(Messages.PLAYLIST_CREATED_WITH_SONG)));
    }

    @Test
    void songsIsClosedToCustomers() throws Exception {
        mockMvc.perform(get(Routes.SONGS).with(user(principal(Role.CUSTOMER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void browseChromeHidesAdminUploadAndCatalogEditsForContentDesigner() throws Exception {
        showing(song("Ice Cream", "Sugar Blizz"));

        mockMvc.perform(get(Routes.SONGS).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(content().string(not(containsString("Add Song"))))
                .andExpect(content().string(not(containsString("Sync Catalog"))))
                .andExpect(content().string(not(containsString("Edit song"))))
                .andExpect(content().string(not(containsString("/admin/catalog/"))))
                .andExpect(content().string(not(containsString("data-edit-song"))));
    }

    /** FT-06: the + in the Add column is how a song reaches a playlist. */
    @Test
    void browseOffersTheAddToPlaylistColumn() throws Exception {
        showing(song("Ice Cream", "Sugar Blizz"));
        given(playlistService.editableDrafts(1L))
                .willReturn(List.of(new PlaylistOption(7L, "Morning coffee", 3)));

        mockMvc.perform(get(Routes.SONGS).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(content().string(containsString(">Add</th>")))
                .andExpect(content().string(containsString("data-add-to-playlist")))
                .andExpect(content().string(containsString("id=\"addToPlaylist\"")))
                .andExpect(content().string(containsString("Morning coffee")));
    }

    /** The dialog is on the page, not inside the swapped results fragment. */
    @Test
    void theAddToPlaylistDialogStaysOutOfThePartial() throws Exception {
        showing(song("Ice Cream", "Sugar Blizz"));

        mockMvc.perform(get(Routes.SONGS)
                        .header(SongBrowseController.PARTIAL_RESULTS_HEADER,
                                SongBrowseController.PARTIAL_RESULTS_VALUE)
                        .with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(content().string(containsString("data-add-to-playlist")))
                .andExpect(content().string(not(containsString("id=\"addToPlaylist\""))));
    }

    @Test
    void contentDesignerSeesSongsInTheSidebar() throws Exception {
        mockMvc.perform(get(Routes.SONGS).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(content().string(containsString("href=\"/songs\"")));
    }

    @Test
    void adminIsRedirectedToTheAdminCatalog() throws Exception {
        mockMvc.perform(get(Routes.SONGS).with(user(principal(Role.ADMIN))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.ADMIN_CATALOG));
    }

    @Test
    void songsPartialReturnsResultsFragmentWithoutShell() throws Exception {
        showing(song("Ice Cream", "Sugar Blizz"));

        mockMvc.perform(get(Routes.SONGS)
                        .header(SongBrowseController.PARTIAL_RESULTS_HEADER,
                                SongBrowseController.PARTIAL_RESULTS_VALUE)
                        .with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("catalog-results")))
                .andExpect(content().string(containsString("Ice Cream")))
                .andExpect(content().string(not(containsString("data-preview-bar"))))
                .andExpect(content().string(not(containsString("sidebar__brand"))));

        then(tagRepository).should(never()).findAllUsedOrderByTypeAscNameAsc();
    }

    @Test
    void songsPassesBrowseFiltersThrough() throws Exception {
        mockMvc.perform(get(Routes.SONGS)
                        .param("provider", "EpidemicSound")
                        .param("genreId", "3")
                        .param("moodId", "4")
                        .param("tagId", "5")
                        .param("q", "ice")
                        .param("page", "1")
                        .with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk());

        then(songCatalogService).should()
                .search(List.of("EpidemicSound"), List.of(3L), List.of(4L), List.of(5L), "ice", 1);
    }

    @Test
    void songsPassesRepeatedBrowseFiltersThrough() throws Exception {
        mockMvc.perform(get(Routes.SONGS)
                        .param("genreId", "3")
                        .param("genreId", "7")
                        .param("moodId", "4")
                        .param("moodId", "8")
                        .with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk());

        then(songCatalogService).should()
                .search(null, List.of(3L, 7L), List.of(4L, 8L), null, null, 0);
    }

    @Test
    void playQueueReturnsJsonForContentDesigner() throws Exception {
        given(songCatalogService.playQueue(nullable(List.class), nullable(List.class),
                nullable(List.class), nullable(List.class), nullable(String.class)))
                .willReturn(List.of(new PreviewTrack(12L, "https://cdn.example/a.mp3", "Alpha",
                        "Sugar Blizz", "https://cdn.example/cover.jpg",
                        "rgba(1, 2, 3, 0.4)", "rgba(4, 5, 6, 0.4)", 180)));

        mockMvc.perform(get(Routes.SONGS_PLAY_QUEUE).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tracks[0].id").value(12))
                .andExpect(jsonPath("$.tracks[0].url").value("https://cdn.example/a.mp3"))
                .andExpect(jsonPath("$.tracks[0].title").value("Alpha"))
                .andExpect(jsonPath("$.tracks[0].artist").value("Sugar Blizz"))
                .andExpect(jsonPath("$.tracks[0].cover").value("https://cdn.example/cover.jpg"))
                .andExpect(jsonPath("$.tracks[0].duration").value(180));
    }

    @Test
    void playQueueIsClosedToCustomers() throws Exception {
        mockMvc.perform(get(Routes.SONGS_PLAY_QUEUE).with(user(principal(Role.CUSTOMER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void playQueueIsOpenToAdminWithoutRedirecting() throws Exception {
        given(songCatalogService.playQueue(nullable(List.class), nullable(List.class),
                nullable(List.class), nullable(List.class), nullable(String.class)))
                .willReturn(List.of());

        mockMvc.perform(get(Routes.SONGS_PLAY_QUEUE).with(user(principal(Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks").isArray());
    }

    @Test
    void playQueuePassesBrowseFiltersThrough() throws Exception {
        given(songCatalogService.playQueue(nullable(List.class), nullable(List.class),
                nullable(List.class), nullable(List.class), nullable(String.class)))
                .willReturn(List.of());

        mockMvc.perform(get(Routes.SONGS_PLAY_QUEUE)
                        .param("provider", "EpidemicSound")
                        .param("genreId", "3")
                        .param("moodId", "4")
                        .param("tagId", "5")
                        .param("q", "ice")
                        .with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk());

        then(songCatalogService).should()
                .playQueue(List.of("EpidemicSound"), List.of(3L), List.of(4L), List.of(5L), "ice");
    }

    @Test
    void emptyBrowseStateDoesNotPointAtTheAdminUpload() throws Exception {
        mockMvc.perform(get(Routes.SONGS).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(content().string(containsString("No songs match")))
                .andExpect(content().string(containsString("clearing the filters")))
                .andExpect(content().string(not(containsString("Add Song"))));
    }

    private void showing(Song... songs) {
        given(songCatalogService.search(nullable(List.class), nullable(List.class),
                nullable(List.class), nullable(List.class), nullable(String.class), anyInt()))
                .willReturn(new PageImpl<>(List.of(songs), PageRequest.of(0, 20), songs.length));
        given(songCatalogService.total()).willReturn((long) songs.length);
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
        song.setTags(Set.of(
                new Tag(TagType.GENRE, "Pop"),
                new Tag(TagType.MOOD, "Dreamy"),
                new Tag(TagType.TAGS, "Female Vocals")));
        return song;
    }
}
