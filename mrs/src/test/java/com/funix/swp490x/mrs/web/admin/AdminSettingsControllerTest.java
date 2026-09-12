package com.funix.swp490x.mrs.web.admin;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
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
import com.funix.swp490x.mrs.domain.CatalogProvider;
import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.LoginAttemptService;
import com.funix.swp490x.mrs.security.LoginFailureHandler;
import com.funix.swp490x.mrs.security.LoginSuccessHandler;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.security.MrsUserDetailsService;
import com.funix.swp490x.mrs.service.CatalogProviderService;
import com.funix.swp490x.mrs.service.CatalogProviderService.ProviderImpact;
import com.funix.swp490x.mrs.service.InvalidSettingsException;
import com.funix.swp490x.mrs.service.SettingsService;
import com.funix.swp490x.mrs.settings.SettingKey;
import com.funix.swp490x.mrs.web.Messages;
import com.funix.swp490x.mrs.web.Routes;
import com.funix.swp490x.mrs.web.support.ShellModelAdvice;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * P-06d / UC-31: ADMIN can save General and LLM settings and manage providers;
 * everyone else is closed out.
 */
@WebMvcTest(controllers = AdminSettingsController.class)
@Import({SecurityConfig.class, WebConfig.class, ShellModelAdvice.class, LoginSuccessHandler.class,
        LoginFailureHandler.class, LoginAttemptService.class, MrsUserDetailsService.class})
class AdminSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SettingsService settingsService;

    @MockitoBean
    private CatalogProviderService catalogProviderService;

    @BeforeEach
    void defaults() {
        given(settingsService.currentValues()).willReturn(defaultSettingValues());
        given(settingsService.sessionInactivityHours()).willReturn(8);
        given(settingsService.lockoutWindow()).willReturn(Duration.ofMinutes(15));
        given(settingsService.resetLinkValidity()).willReturn(Duration.ofMinutes(30));
        given(catalogProviderService.listWithImpact()).willReturn(List.of());
    }

    @Test
    void adminCanOpenTheLiveForm() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_SETTINGS).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("General configuration")))
                .andExpect(content().string(containsString(">Save</button>")))
                .andExpect(content().string(containsString(">Reset</button>")))
                .andExpect(content().string(containsString("name=\"llm.model\"")))
                .andExpect(content().string(containsString("<select")))
                .andExpect(content().string(containsString("gemini-3.8-flash")))
                .andExpect(content().string(containsString("gemini-2.5-flash")));
    }

    @Test
    void savePersistsAndFlashesMsg025() throws Exception {
        given(settingsService.update(any(), eq(1L))).willReturn(1);

        mockMvc.perform(saveRequest(Map.of()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.ADMIN_SETTINGS + "#settings-actions"))
                .andExpect(flash().attribute("flash", Messages.SETTINGS_SAVED))
                .andExpect(flash().attribute("flashPlacement", "actions"));

        then(settingsService).should().update(any(), eq(1L));
    }

    @Test
    void unchangedSaveSaysSo() throws Exception {
        given(settingsService.update(any(), eq(1L))).willReturn(0);

        mockMvc.perform(saveRequest(Map.of()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flash", Messages.SETTINGS_UNCHANGED))
                .andExpect(flash().attribute("flashVariant", "info"));
    }

    @Test
    void resetRestoresDefaults() throws Exception {
        given(settingsService.resetToDefaults(1L)).willReturn(2);

        mockMvc.perform(post(Routes.ADMIN_SETTINGS_RESET)
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.ADMIN_SETTINGS + "#settings-actions"))
                .andExpect(flash().attribute("flash", Messages.SETTINGS_RESET));

        then(settingsService).should().resetToDefaults(1L);
    }

    @Test
    void resetWhenAlreadyDefaultSaysSo() throws Exception {
        given(settingsService.resetToDefaults(1L)).willReturn(0);

        mockMvc.perform(post(Routes.ADMIN_SETTINGS_RESET)
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(flash().attribute("flash", Messages.SETTINGS_ALREADY_DEFAULT));
    }

    @Test
    void outOfRangeSaveReturns422AndKeepsTheForm() throws Exception {
        willThrow(new InvalidSettingsException(Map.of(
                SettingKey.LLM_TIMEOUT_SECONDS,
                SettingsService.message(SettingKey.LLM_TIMEOUT_SECONDS))))
                .given(settingsService).update(any(), any());

        mockMvc.perform(saveRequest(Map.of(SettingKey.LLM_TIMEOUT_SECONDS, "31")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString(
                        SettingsService.message(SettingKey.LLM_TIMEOUT_SECONDS))))
                .andExpect(content().string(containsString(Messages.SETTINGS_NOT_SAVED)));
    }

    @Test
    void contentDesignerCannotSave() throws Exception {
        mockMvc.perform(post(Routes.ADMIN_SETTINGS_SAVE)
                        .param(SettingKey.LLM_TIMEOUT_SECONDS.key(), "5")
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        then(settingsService).should(never()).update(any(), any());
    }

    @Test
    void contentDesignerCannotReset() throws Exception {
        mockMvc.perform(post(Routes.ADMIN_SETTINGS_RESET)
                        .with(user(principal(Role.CONTENT_DESIGNER)))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        then(settingsService).should(never()).resetToDefaults(any());
    }

    @Test
    void customerCannotOpenSettings() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_SETTINGS).with(user(principal(Role.CUSTOMER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createProviderRedirectsWithASuccessFlash() throws Exception {
        mockMvc.perform(post(Routes.ADMIN_SETTINGS_PROVIDERS)
                        .param("name", "Artlist")
                        .param("slug", "artlist")
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(Routes.ADMIN_SETTINGS))
                .andExpect(flash().attribute("flash", Messages.PROVIDER_CREATED));

        then(catalogProviderService).should().create("Artlist", "artlist", 1L);
    }

    @Test
    void deleteProviderReportsHowManySongsWentWithIt() throws Exception {
        CatalogProvider ncs = new CatalogProvider("NCS", "ncs");
        ReflectionTestUtils.setField(ncs, "id", 3L);
        given(catalogProviderService.delete(3L, "NCS", 1L))
                .willReturn(new ProviderImpact(ncs, 2L, 1L));

        mockMvc.perform(post("/admin/settings/providers/3/delete")
                        .param("confirmName", "NCS")
                        .with(user(admin()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flash", Messages.providerDeleted("NCS", 2L)));
    }

    private MockHttpServletRequestBuilder saveRequest(Map<SettingKey, String> overrides) {
        MockHttpServletRequestBuilder request = post(Routes.ADMIN_SETTINGS_SAVE)
                .with(user(admin()))
                .with(csrf());
        for (SettingKey key : SettingKey.values()) {
            request.param(key.key(), overrides.getOrDefault(key, key.defaultValue()));
        }
        return request;
    }

    private static Map<SettingKey, String> defaultSettingValues() {
        Map<SettingKey, String> values = new EnumMap<>(SettingKey.class);
        for (SettingKey key : SettingKey.values()) {
            values.put(key, key.defaultValue());
        }
        return values;
    }

    private static MrsUserDetails admin() {
        return principal(Role.ADMIN);
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
