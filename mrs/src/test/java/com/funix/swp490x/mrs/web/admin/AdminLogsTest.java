package com.funix.swp490x.mrs.web.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funix.swp490x.mrs.config.SecurityConfig;
import com.funix.swp490x.mrs.config.WebConfig;
import com.funix.swp490x.mrs.domain.AuditLog;
import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.security.LoginAttemptService;
import com.funix.swp490x.mrs.security.LoginFailureHandler;
import com.funix.swp490x.mrs.security.LoginSuccessHandler;
import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.security.MrsUserDetailsService;
import com.funix.swp490x.mrs.service.AdminLogService;
import com.funix.swp490x.mrs.service.AdminLogService.AuditLogView;
import com.funix.swp490x.mrs.service.AdminLogService.LogPerson;
import com.funix.swp490x.mrs.service.AdminLogService.RecommendationLogView;
import com.funix.swp490x.mrs.web.Routes;
import com.funix.swp490x.mrs.web.support.ShellModelAdvice;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * P-06e / UC-32: ADMIN browses audit and recommendation logs; everyone else is
 * closed out. There is no delete endpoint.
 */
@WebMvcTest(controllers = AdminLogsController.class)
@Import({SecurityConfig.class, WebConfig.class, ShellModelAdvice.class, LoginSuccessHandler.class,
        LoginFailureHandler.class, LoginAttemptService.class, MrsUserDetailsService.class})
class AdminLogsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AdminLogService adminLogService;

    @BeforeEach
    void emptyLogsByDefault() {
        given(adminLogService.searchAudit(nullable(Long.class), nullable(String.class),
                nullable(LocalDate.class), nullable(LocalDate.class), anyInt()))
                .willReturn(Page.empty());
        given(adminLogService.searchRecommendations(nullable(Long.class), nullable(String.class),
                nullable(LocalDate.class), nullable(LocalDate.class), anyInt()))
                .willReturn(Page.empty());
        given(adminLogService.actors()).willReturn(List.of());
        given(adminLogService.searchUsers()).willReturn(List.of());
        given(adminLogService.actions()).willReturn(List.of(AuditLog.ACTION_USER_CREATE));
    }

    @Test
    void adminSeesAuditRowsAndNoPlaceholders() throws Exception {
        given(adminLogService.searchAudit(isNull(), isNull(), isNull(), isNull(), eq(0)))
                .willReturn(new PageImpl<>(List.of(auditView()), PageRequest.of(0, 20), 1));
        given(adminLogService.actors())
                .willReturn(List.of(new LogPerson(3L, "System Admin (admin@mrs.local)")));

        mockMvc.perform(get(Routes.ADMIN_LOGS).with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Audit")))
                .andExpect(content().string(containsString("Recommendation")))
                .andExpect(content().string(containsString("System Admin (admin@mrs.local)")))
                .andExpect(content().string(containsString("USER_CREATE")))
                .andExpect(content().string(containsString("USER #42")))
                .andExpect(content().string(containsString("Before / after")))
                .andExpect(content().string(containsString("retained for 12 months")))
                .andExpect(content().string(not(containsString("zone-placeholder"))));
    }

    @Test
    void emptyAuditFilterShowsTheEmptyState() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_LOGS)
                        .param("action", AuditLog.ACTION_SONG_DELETE)
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No audit entries")))
                .andExpect(content().string(not(containsString("zone-placeholder"))));
    }

    @Test
    void auditFiltersArePassedThrough() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_LOGS)
                        .param("tab", "audit")
                        .param("actorId", "3")
                        .param("action", AuditLog.ACTION_USER_CREATE)
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31")
                        .param("page", "1")
                        .with(user(admin())))
                .andExpect(status().isOk());

        then(adminLogService).should().searchAudit(eq(3L), eq(AuditLog.ACTION_USER_CREATE),
                eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 1, 31)), eq(1));
        then(adminLogService).should(never())
                .searchRecommendations(nullable(Long.class), nullable(String.class),
                        nullable(LocalDate.class), nullable(LocalDate.class), anyInt());
    }

    @Test
    void recommendationTabShowsLlmOutcomeAndEmptyState() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_LOGS)
                        .param("tab", "recommendation")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No recommendation entries")))
                .andExpect(content().string(containsString("LLM outcome")))
                .andExpect(content().string(not(containsString("No audit entries"))));

        then(adminLogService).should().searchRecommendations(isNull(), isNull(), isNull(), isNull(),
                eq(0));
        then(adminLogService).should(never()).searchAudit(nullable(Long.class), nullable(String.class),
                nullable(LocalDate.class), nullable(LocalDate.class), anyInt());
    }

    @Test
    void recommendationFiltersAndRowsRender() throws Exception {
        given(adminLogService.searchRecommendations(eq(8L), eq("fallback"),
                eq(LocalDate.of(2026, 2, 1)), eq(LocalDate.of(2026, 2, 28)), eq(0)))
                .willReturn(new PageImpl<>(List.of(recommendationView()), PageRequest.of(0, 20), 1));
        given(adminLogService.searchUsers())
                .willReturn(List.of(new LogPerson(8L, "Dana (dana@mrs.local)")));

        mockMvc.perform(get(Routes.ADMIN_LOGS)
                        .param("tab", "recommendation")
                        .param("userId", "8")
                        .param("llm", "fallback")
                        .param("from", "2026-02-01")
                        .param("to", "2026-02-28")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dana (dana@mrs.local)")))
                .andExpect(content().string(containsString("cheerful new year")))
                .andExpect(content().string(containsString("Fallback")))
                .andExpect(content().string(containsString(">12<")));
    }

    @Test
    void pagerKeepsTheActiveTabAndFilters() throws Exception {
        given(adminLogService.searchAudit(eq(3L), eq(AuditLog.ACTION_USER_CREATE),
                isNull(), isNull(), eq(0)))
                .willReturn(new PageImpl<>(List.of(auditView()), PageRequest.of(0, 20), 21));

        mockMvc.perform(get(Routes.ADMIN_LOGS)
                        .param("actorId", "3")
                        .param("action", AuditLog.ACTION_USER_CREATE)
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Page")))
                .andExpect(content().string(containsString("21")))
                .andExpect(content().string(containsString("tab=audit")))
                .andExpect(content().string(containsString("actorId=3")))
                .andExpect(content().string(containsString("action=USER_CREATE")));
    }

    @Test
    void contentDesignerCannotOpenLogs() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_LOGS).with(user(principal(Role.CONTENT_DESIGNER))))
                .andExpect(status().isForbidden());

        then(adminLogService).shouldHaveNoInteractions();
    }

    @Test
    void customerCannotOpenLogs() throws Exception {
        mockMvc.perform(get(Routes.ADMIN_LOGS).with(user(principal(Role.CUSTOMER))))
                .andExpect(status().isForbidden());

        then(adminLogService).shouldHaveNoInteractions();
    }

    private static AuditLogView auditView() {
        return new AuditLogView(9L, LocalDateTime.of(2026, 1, 12, 9, 0), 3L,
                "System Admin (admin@mrs.local)", AuditLog.ACTION_USER_CREATE,
                AuditLog.ENTITY_USER, 42L, "USER #42", "{\n  \"email\" : \"nina@mrs.local\"\n}");
    }

    private static RecommendationLogView recommendationView() {
        return new RecommendationLogView(4L, LocalDateTime.of(2026, 2, 3, 10, 15), 8L,
                "Dana (dana@mrs.local)", "cheerful new year", "{\n  \"moods\" : [ \"Cheerful\" ]\n}",
                "Fallback", 12);
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
