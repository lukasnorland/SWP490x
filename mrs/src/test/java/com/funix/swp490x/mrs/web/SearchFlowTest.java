package com.funix.swp490x.mrs.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
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
import com.funix.swp490x.mrs.service.DuplicatePlaylistNameException;
import com.funix.swp490x.mrs.service.InvalidSearchQueryException;
import com.funix.swp490x.mrs.service.PlaylistService;
import com.funix.swp490x.mrs.service.SearchService;
import com.funix.swp490x.mrs.web.support.ShellModelAdvice;
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

@WebMvcTest(controllers = SearchController.class)
@Import({SecurityConfig.class, WebConfig.class, ShellModelAdvice.class, LoginSuccessHandler.class,
        LoginFailureHandler.class, LoginAttemptService.class, MrsUserDetailsService.class})
class SearchFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SearchService searchService;

    @MockitoBean
    private PlaylistService playlistService;

    @BeforeEach
    void defaults() {
        given(searchService.search(nullable(List.class), nullable(List.class),
                nullable(List.class), nullable(List.class), nullable(String.class),
                nullable(Integer.class), anyInt()))
                .willReturn(Page.empty());
        given(searchService.chips(nullable(List.class), nullable(List.class),
                nullable(List.class), nullable(List.class), nullable(String.class),
                nullable(String.class), nullable(Integer.class)))
                .willReturn(List.of());
        given(searchService.filterTags()).willReturn(List.of());
        given(playlistService.editableDrafts(nullable(Long.class))).willReturn(List.of());
    }

    /**
     * "Create and add" from a search row and a failed "Create playlist from
     * results" both redirect back here with a flash; the page has to show it,
     * or the outcome is invisible and the form looks like it did nothing.
     */
    @Test
    void searchShowsTheFlashItWasRedirectedBackWith() throws Exception {
        mockMvc.perform(get(Routes.SEARCH)
                        .flashAttr("flash", Messages.PLAYLIST_CREATED_WITH_SONG)
                        .flashAttr("flashVariant", "success")
                        .with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(Messages.PLAYLIST_CREATED_WITH_SONG)));

        mockMvc.perform(get(Routes.SEARCH)
                        .flashAttr("flash", Messages.SEARCH_NO_RESULTS_TO_ADD)
                        .flashAttr("flashVariant", "warning")
                        .with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("refine it first")));
    }

    /**
     * Zone A (the prompt) and Zone B (the metadata filter panel) both belong to
     * P-02: UC-10 is filter-first, UC-11 only seeds the same filters.
     */
    @Test
    void searchRendersThePromptAndTheFilterPanelForCurators() throws Exception {
        mockMvc.perform(get(Routes.SEARCH).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Playlist need")))
                .andExpect(content().string(containsString("/search/interpret")))
                .andExpect(content().string(containsString("id=\"searchTopN\"")))
                .andExpect(content().string(containsString("data-catalog-filters")))
                .andExpect(content().string(containsString("id=\"filterGenre\"")))
                .andExpect(content().string(containsString("id=\"filterMood\"")))
                .andExpect(content().string(containsString("id=\"filterArtist\"")))
                .andExpect(content().string(containsString("id=\"filterTag\"")))
                .andExpect(content().string(containsString("id=\"filterTopN\"")))
                // Provider is ADMIN catalog work, and untagged-only is P-06b.
                .andExpect(content().string(not(containsString("id=\"filterProvider\""))))
                .andExpect(content().string(not(containsString("id=\"filterUntagged\""))));
    }

    /** MSG_006 rather than the first-visit copy, once filters are in play. */
    @Test
    void filtersThatMatchNothingReportMsg006() throws Exception {
        mockMvc.perform(get(Routes.SEARCH).param("genreId", "2").param("moodId", "1")
                        .with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(Messages.SEARCH_NO_MATCHES)));

        mockMvc.perform(get(Routes.SEARCH).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(Messages.SEARCH_NO_MATCHES))));
    }

    /**
     * FT-05 NAC-03 / BV-05: 0 and negatives are a rejected request (HTTP 422),
     * not a shorthand for "every match", so no results are listed.
     */
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void aTopNBelowOneIsRejectedInline(String topN) throws Exception {
        mockMvc.perform(get(Routes.SEARCH).param("genreId", "2").param("topN", topN)
                        .with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString(Messages.SEARCH_TOP_N_POSITIVE)))
                .andExpect(content().string(containsString("is-invalid")))
                .andExpect(content().string(containsString("value=\"" + topN + "\"")));

        then(searchService).should(org.mockito.Mockito.never())
                .search(anyList(), anyList(), anyList(), anyList(), nullable(String.class),
                        nullable(Integer.class), anyInt());
    }

    @Test
    void aPositiveTopNReachesTheService() throws Exception {
        mockMvc.perform(get(Routes.SEARCH).param("genreId", "2").param("topN", "1")
                        .with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(Messages.SEARCH_TOP_N_POSITIVE))));

        then(searchService).should().search(eq(List.of(2L)), nullable(List.class),
                nullable(List.class), nullable(List.class), nullable(String.class), eq(1), eq(0));
    }

    @Test
    void searchIsClosedToCustomers() throws Exception {
        mockMvc.perform(get(Routes.SEARCH).with(user(principal(Role.CUSTOMER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void interpretRedirectsToFilterQuery() throws Exception {
        given(searchService.interpretRedirect(eq(1L), eq("energetic pop playlist now"),
                nullable(Integer.class)))
                .willReturn(new SearchService.InterpretRedirect("/search?genreId=2&moodId=1", false));

        mockMvc.perform(post(Routes.SEARCH_INTERPRET).with(csrf())
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .param("q", "energetic pop playlist now"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/search?genreId=2&moodId=1"));
    }

    @Test
    void interpretRejectsShortQueries() throws Exception {
        willThrow(new InvalidSearchQueryException("too short"))
                .given(searchService).interpretRedirect(eq(1L), eq("short"), nullable(Integer.class));

        mockMvc.perform(post(Routes.SEARCH_INTERPRET).with(csrf())
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .param("q", "short"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(Routes.SEARCH))
                .andExpect(flash().attribute("flash", Messages.SEARCH_QUERY_LENGTH));
    }

    @Test
    void interpretFlashesWhenTheInterpreterFallsBackToKeywords() throws Exception {
        given(searchService.interpretRedirect(eq(1L), eq("upbeat summer campaign for a beach game"),
                nullable(Integer.class)))
                .willReturn(new SearchService.InterpretRedirect(
                        "/search?q=upbeat+summer+campaign+for+a+beach+game", true));

        mockMvc.perform(post(Routes.SEARCH_INTERPRET).with(csrf())
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .param("q", "upbeat summer campaign for a beach game"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/search?q=upbeat+summer+campaign+for+a+beach+game"))
                .andExpect(flash().attribute("flash", Messages.SEARCH_INTERPRET_FALLBACK))
                .andExpect(flash().attribute("flashVariant", "warning"));
    }

    @Test
    void interpretIsClosedToCustomers() throws Exception {
        mockMvc.perform(post(Routes.SEARCH_INTERPRET).with(csrf())
                        .with(user(principal(Role.CUSTOMER)))
                        .param("q", "energetic pop playlist now"))
                .andExpect(status().isForbidden());
        then(searchService).shouldHaveNoInteractions();
    }

    @Test
    void resultsOfferCreatePlaylistFromResultsToCurators() throws Exception {
        Song song = new Song();
        song.setId(7L);
        song.setTitle("Ice Cream");
        song.setArtist("Sugar Blizz");
        song.setSourceProvider("EpidemicSound");
        song.setExternalSourceId("ext-7");
        song.setTags(java.util.Set.of());
        given(searchService.search(nullable(List.class), nullable(List.class),
                nullable(List.class), nullable(List.class), eq("summer"),
                nullable(Integer.class), anyInt()))
                .willReturn(new PageImpl<>(List.of(song), PageRequest.of(0, 20), 41));

        mockMvc.perform(get(Routes.SEARCH).param("q", "summer")
                        .with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-create-from-results")))
                .andExpect(content().string(containsString("data-result-count=\"41\"")))
                .andExpect(content().string(containsString("Create playlist from results")))
                .andExpect(content().string(containsString("/search/create-playlist")));
    }

    /**
     * The two dialog modes toggle [hidden] on plain wrappers. Putting it on the
     * .d-flex forms themselves left both "Create and add" and "Create" showing
     * at once, and the wrong one posted with no criteria.
     */
    @Test
    void theDialogModesAreWrappedSoOnlyOneShowsAtATime() throws Exception {
        mockMvc.perform(get(Routes.SEARCH).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "class=\"mode-stack\" data-add-song-mode")))
                .andExpect(content().string(containsString(
                        "class=\"mode-stack\" data-create-from-results-mode hidden")))
                .andExpect(content().string(not(containsString(
                        "data-create-from-results-form hidden"))));
    }

    @Test
    void emptyResultsDoNotOfferCreatePlaylistFromResults() throws Exception {
        mockMvc.perform(get(Routes.SEARCH).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Create playlist from results"))))
                .andExpect(content().string(not(containsString("data-result-count"))));
    }

    @Test
    void createPlaylistFromResultsAddsEveryMatchAndOpensThePlaylist() throws Exception {
        given(searchService.resultSongIds(eq(List.of(2L)), eq(List.of(1L)), nullable(List.class),
                nullable(List.class), nullable(String.class), eq(25)))
                .willReturn(List.of(7L, 3L, 9L));
        Playlist created = new Playlist("Summer beach", 1L);
        ReflectionTestUtils.setField(created, "id", 12L);
        given(playlistService.createWithSongs(1L, "Summer beach", List.of(7L, 3L, 9L)))
                .willReturn(created);

        mockMvc.perform(post(Routes.SEARCH_CREATE_PLAYLIST).with(csrf())
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .param("name", "Summer beach")
                        .param("genreId", "2")
                        .param("moodId", "1")
                        .param("topN", "25")
                        .param("returnTo", "/search?genreId=2&moodId=1&topN=25"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/playlists/12"))
                .andExpect(flash().attribute("flash", Messages.PLAYLIST_CREATED_FROM_RESULTS));

        then(playlistService).should().createWithSongs(1L, "Summer beach", List.of(7L, 3L, 9L));
    }

    @Test
    void createPlaylistFromResultsWarnsWhenTheNameIsTaken() throws Exception {
        given(searchService.resultSongIds(nullable(List.class), nullable(List.class),
                nullable(List.class), nullable(List.class), nullable(String.class),
                nullable(Integer.class)))
                .willReturn(List.of(7L));
        willThrow(new DuplicatePlaylistNameException("Summer beach"))
                .given(playlistService).createWithSongs(eq(1L), eq("Summer beach"), anyList());

        mockMvc.perform(post(Routes.SEARCH_CREATE_PLAYLIST).with(csrf())
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .param("name", "Summer beach")
                        .param("returnTo", "/search?q=beach"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/search?q=beach"))
                .andExpect(flash().attribute("flash", Messages.PLAYLIST_NAME_TAKEN));
    }

    @Test
    void createPlaylistFromResultsWarnsWhenNothingMatches() throws Exception {
        given(searchService.resultSongIds(nullable(List.class), nullable(List.class),
                nullable(List.class), nullable(List.class), eq("nothing here"),
                nullable(Integer.class)))
                .willReturn(List.of());

        mockMvc.perform(post(Routes.SEARCH_CREATE_PLAYLIST).with(csrf())
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .param("name", "Empty")
                        .param("q", "nothing here")
                        .param("returnTo", "/search?q=nothing+here"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/search?q=nothing+here"))
                .andExpect(flash().attribute("flash", Messages.SEARCH_NO_RESULTS_TO_ADD));

        then(playlistService).should(org.mockito.Mockito.never())
                .createWithSongs(nullable(Long.class), nullable(String.class), anyList());
    }

    @Test
    void createPlaylistFromResultsIgnoresForeignReturnTo() throws Exception {
        given(searchService.resultSongIds(nullable(List.class), nullable(List.class),
                nullable(List.class), nullable(List.class), eq("x y z"),
                nullable(Integer.class)))
                .willReturn(List.of());

        mockMvc.perform(post(Routes.SEARCH_CREATE_PLAYLIST).with(csrf())
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .param("name", "Empty")
                        .param("q", "x y z")
                        .param("returnTo", "//evil.example/search"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(Routes.SEARCH));
    }

    @Test
    void createPlaylistFromResultsIsClosedToCustomers() throws Exception {
        mockMvc.perform(post(Routes.SEARCH_CREATE_PLAYLIST).with(csrf())
                        .with(user(principal(Role.CUSTOMER)))
                        .param("name", "Nope")
                        .param("q", "energetic pop"))
                .andExpect(status().isForbidden());
        then(playlistService).shouldHaveNoInteractions();
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
