package com.funix.swp490x.mrs.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import com.funix.swp490x.mrs.service.SongCatalogService;
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

    @BeforeEach
    void defaults() {
        given(songCatalogService.search(nullable(String.class), nullable(Long.class),
                nullable(String.class), anyBoolean(), anyBoolean(), anyInt()))
                .willReturn(Page.empty());
        given(songCatalogService.providers()).willReturn(List.of("EpidemicSound"));
        given(songCatalogService.total()).willReturn(0L);
        given(tagRepository.findAllByOrderByTypeAscNameAsc()).willReturn(List.of());
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
                .andExpect(content().string(not(containsString("Untagged only"))));
    }

    @Test
    void songsIsClosedToCustomers() throws Exception {
        mockMvc.perform(get(Routes.SONGS).with(user(principal(Role.CUSTOMER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void browseChromeHidesAdminImportAndCatalogEditsForContentDesigner() throws Exception {
        showing(song("Ice Cream", "Sugar Blizz"));

        mockMvc.perform(get(Routes.SONGS).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(content().string(not(containsString("Catalog Import"))))
                .andExpect(content().string(not(containsString("Edit song"))))
                .andExpect(content().string(not(containsString("/admin/catalog/"))))
                .andExpect(content().string(not(containsString("data-edit-song"))));
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
    }

    @Test
    void songsPassesBrowseFiltersThrough() throws Exception {
        mockMvc.perform(get(Routes.SONGS)
                        .param("provider", "EpidemicSound")
                        .param("tagId", "5")
                        .param("q", "ice")
                        .param("noPreview", "true")
                        .param("page", "1")
                        .with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk());

        then(songCatalogService).should()
                .search("EpidemicSound", 5L, "ice", false, true, 1);
    }

    @Test
    void emptyBrowseStateDoesNotPointAtAdminImport() throws Exception {
        mockMvc.perform(get(Routes.SONGS).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(content().string(containsString("No songs match")))
                .andExpect(content().string(containsString("clearing the filters")))
                .andExpect(content().string(not(containsString("/admin/import"))));
    }

    private void showing(Song... songs) {
        given(songCatalogService.search(nullable(String.class), nullable(Long.class),
                nullable(String.class), anyBoolean(), anyBoolean(), anyInt()))
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
        song.setTags(Set.of(new Tag(TagType.GENRE, "Pop")));
        return song;
    }
}
