package com.funix.swp490x.mrs.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
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
import com.funix.swp490x.mrs.service.AuthService;
import com.funix.swp490x.mrs.service.AuthService.DisplayNameResult;
import com.funix.swp490x.mrs.service.AuthService.PasswordChangeResult;
import com.funix.swp490x.mrs.web.support.ShellModelAdvice;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * P-05 / UC-05: the signed-in account can change its display name and
 * password, and cannot change its own role.
 */
@WebMvcTest(controllers = ProfileController.class)
@Import({SecurityConfig.class, WebConfig.class, ShellModelAdvice.class, LoginSuccessHandler.class,
        LoginFailureHandler.class, LoginAttemptService.class, MrsUserDetailsService.class})
class ProfileFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthService authService;

    @Test
    void profileScreenOffersDisplayNameAndPasswordForms() throws Exception {
        mockMvc.perform(get(Routes.PROFILE).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"displayName\"")))
                .andExpect(content().string(containsString("Nina Designer")))
                .andExpect(content().string(containsString("Change password")))
                .andExpect(content().string(containsString("data-password-policy")))
                .andExpect(content().string(not(containsString("Playlist history"))))
                .andExpect(content().string(not(containsString("Editable display name"))));
    }

    @Test
    void customersCanOpenTheirOwnProfile() throws Exception {
        mockMvc.perform(get(Routes.PROFILE).with(user(principal(Role.CUSTOMER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"displayName\"")));
    }

    @Test
    void savingADisplayNameRedirectsWithAFlashAndDoesNotAskForRelogin() throws Exception {
        given(authService.updateDisplayName("nina@mrs.local", "Nina Updated"))
                .willReturn(new DisplayNameResult("Nina Updated", List.of()));

        mockMvc.perform(post(Routes.PROFILE_NAME)
                        .param("displayName", "Nina Updated")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.PROFILE))
                .andExpect(flash().attribute("flash", Messages.PROFILE_NAME_SAVED))
                .andExpect(flash().attribute("flashVariant", "success"));
    }

    @Test
    void aBlankDisplayNameStaysOnTheScreenWithHttp422() throws Exception {
        given(authService.updateDisplayName(eq("nina@mrs.local"), anyString()))
                .willReturn(new DisplayNameResult(null, List.of("Enter a display name")));

        mockMvc.perform(post(Routes.PROFILE_NAME)
                        .param("displayName", "   ")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("Enter a display name")));
    }

    @Test
    void savingAPasswordRedirectsWithAFlash() throws Exception {
        given(authService.changePassword("nina@mrs.local", "Admin@2026", "FreshPass@2026",
                "FreshPass@2026"))
                .willReturn(new PasswordChangeResult(List.of(), Routes.SEARCH));

        mockMvc.perform(post(Routes.PROFILE_PASSWORD)
                        .param("currentPassword", "Admin@2026")
                        .param("password", "FreshPass@2026")
                        .param("confirmPassword", "FreshPass@2026")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.PROFILE))
                .andExpect(flash().attribute("flash", Messages.PROFILE_PASSWORD_SAVED));
    }

    @Test
    void aWeakPasswordStaysOnTheScreenWithHttp422() throws Exception {
        given(authService.changePassword(eq("nina@mrs.local"), anyString(), anyString(),
                anyString()))
                .willReturn(new PasswordChangeResult(List.of("Include an uppercase letter"), null));

        mockMvc.perform(post(Routes.PROFILE_PASSWORD)
                        .param("currentPassword", "Admin@2026")
                        .param("password", "weakpass1!")
                        .param("confirmPassword", "weakpass1!")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("Include an uppercase letter")));
    }

    @Test
    void postingARoleFieldIsForbiddenAndChangesNothing() throws Exception {
        mockMvc.perform(post(Routes.PROFILE_NAME)
                        .param("displayName", "Nina Updated")
                        .param("role", "ADMIN")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        then(authService).should(never()).updateDisplayName(anyString(), anyString());
    }

    @Test
    void aDirectPostToTheProfileUrlIsForbidden() throws Exception {
        mockMvc.perform(post(Routes.PROFILE)
                        .param("role", "ADMIN")
                        .with(user(principal(Role.CUSTOMER)))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedVisitorsAreSentToLogin() throws Exception {
        mockMvc.perform(get(Routes.PROFILE))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.LOGIN));
    }

    private static MrsUserDetails principal(Role role) {
        User user = new User();
        user.setId(2L);
        user.setUsername("Nina Designer");
        user.setEmail("nina@mrs.local");
        user.setPasswordHash("{noop}irrelevant");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(false);
        return new MrsUserDetails(user, true);
    }
}
