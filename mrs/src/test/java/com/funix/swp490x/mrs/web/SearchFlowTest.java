package com.funix.swp490x.mrs.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.LoginAttemptService;
import com.funix.swp490x.mrs.security.LoginFailureHandler;
import com.funix.swp490x.mrs.security.LoginSuccessHandler;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.security.MrsUserDetailsService;
import com.funix.swp490x.mrs.service.InvalidSearchQueryException;
import com.funix.swp490x.mrs.service.PlaylistService;
import com.funix.swp490x.mrs.service.SearchService;
import com.funix.swp490x.mrs.web.support.ShellModelAdvice;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
                nullable(Integer.class)))
                .willReturn(List.of());
        given(playlistService.editableDrafts(nullable(Long.class))).willReturn(List.of());
    }

    @Test
    void searchRendersPromptForCurators() throws Exception {
        mockMvc.perform(get(Routes.SEARCH).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Playlist need")))
                .andExpect(content().string(containsString("/search/interpret")))
                .andExpect(content().string(containsString("id=\"searchTopN\"")))
                .andExpect(content().string(not(containsString("data-catalog-filters"))))
                .andExpect(content().string(not(containsString("id=\"filterGenre\""))));
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
                .willReturn("/search?genreId=2&moodId=1");

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
    void interpretIsClosedToCustomers() throws Exception {
        mockMvc.perform(post(Routes.SEARCH_INTERPRET).with(csrf())
                        .with(user(principal(Role.CUSTOMER)))
                        .param("q", "energetic pop playlist now"))
                .andExpect(status().isForbidden());
        then(searchService).shouldHaveNoInteractions();
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
