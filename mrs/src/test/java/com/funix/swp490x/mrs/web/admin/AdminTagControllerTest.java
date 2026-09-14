package com.funix.swp490x.mrs.web.admin;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.funix.swp490x.mrs.domain.Tag;
import com.funix.swp490x.mrs.domain.TagType;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.LoginAttemptService;
import com.funix.swp490x.mrs.security.LoginFailureHandler;
import com.funix.swp490x.mrs.security.LoginSuccessHandler;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.security.MrsUserDetailsService;
import com.funix.swp490x.mrs.service.TagVocabularyException;
import com.funix.swp490x.mrs.service.TagVocabularyService;
import com.funix.swp490x.mrs.service.TagVocabularyService.TagRow;
import com.funix.swp490x.mrs.web.Messages;
import com.funix.swp490x.mrs.web.Routes;
import com.funix.swp490x.mrs.web.support.ShellModelAdvice;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminTagController.class)
@Import({SecurityConfig.class, WebConfig.class, ShellModelAdvice.class, LoginSuccessHandler.class,
        LoginFailureHandler.class, LoginAttemptService.class, MrsUserDetailsService.class})
class AdminTagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private TagVocabularyService tagVocabularyService;

    @BeforeEach
    void defaults() {
        given(tagVocabularyService.list()).willReturn(List.of());
    }

    @Test
    void tagsTabListsUnusedAndInUseRows() throws Exception {
        given(tagVocabularyService.list()).willReturn(List.of(
                new TagRow(1L, TagType.GENRE, "Pop", 4L),
                new TagRow(2L, TagType.GENRE, "Jazz", 0L)));

        mockMvc.perform(get(Routes.ADMIN_CATALOG_TAGS).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tag dictionary")))
                .andExpect(content().string(containsString("Pop")))
                .andExpect(content().string(containsString("Jazz")))
                .andExpect(content().string(containsString("In use")))
                .andExpect(content().string(containsString("Rename")))
                .andExpect(content().string(containsString("Delete")));
    }

    @Test
    void createAddsADictionaryRow() throws Exception {
        given(tagVocabularyService.create(TagType.GENRE, "Pop")).willReturn(new Tag(TagType.GENRE, "Pop"));

        mockMvc.perform(post(Routes.ADMIN_CATALOG_TAGS)
                        .param("type", "GENRE")
                        .param("name", "Pop")
                        .with(csrf())
                        .with(user(admin())))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(Routes.ADMIN_CATALOG_TAGS))
                .andExpect(flash().attribute("flash", Messages.TAG_CREATED));

        then(tagVocabularyService).should().create(TagType.GENRE, "Pop");
    }

    @Test
    void createFlashesValidationFailures() throws Exception {
        willThrow(new TagVocabularyException("Pick a listed MusicBrainz genre."))
                .given(tagVocabularyService).create(eq(TagType.GENRE), any());

        mockMvc.perform(post(Routes.ADMIN_CATALOG_TAGS)
                        .param("type", "GENRE")
                        .param("name", "Nope")
                        .with(csrf())
                        .with(user(admin())))
                .andExpect(redirectedUrl(Routes.ADMIN_CATALOG_TAGS))
                .andExpect(flash().attribute("flash", "Pick a listed MusicBrainz genre."));
    }

    @Test
    void tagsTabIsClosedToContentDesigners() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_CATALOG_TAGS)
                        .with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isForbidden());
    }

    private static MrsUserDetails admin() {
        return principal(Role.ADMIN);
    }

    private static MrsUserDetails principal(Role role) {
        User user = new User();
        user.setId(7L);
        user.setUsername("Test " + role.getDisplayName());
        user.setEmail(role.name().toLowerCase() + "@mrs.local");
        user.setPasswordHash("{noop}irrelevant");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(false);
        return new MrsUserDetails(user, true);
    }
}
