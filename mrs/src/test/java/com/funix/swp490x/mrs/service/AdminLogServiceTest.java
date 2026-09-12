package com.funix.swp490x.mrs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.funix.swp490x.mrs.domain.AuditLog;
import com.funix.swp490x.mrs.domain.RecommendationLog;
import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.RecommendationLogRepository;
import com.funix.swp490x.mrs.repository.UserRepository;
import com.funix.swp490x.mrs.service.AdminLogService.AuditLogView;
import com.funix.swp490x.mrs.service.AdminLogService.RecommendationLogView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private RecommendationLogRepository recommendationLogRepository;
    @Mock
    private UserRepository userRepository;

    private AdminLogService service;

    @BeforeEach
    void setUp() {
        service = new AdminLogService(auditLogRepository, recommendationLogRepository,
                userRepository);
    }

    @Test
    void searchAuditLabelsTheActorAndPrettyPrintsDetails() {
        AuditLog row = new AuditLog(3L, AuditLog.ACTION_USER_CREATE, AuditLog.ENTITY_USER, 42L,
                "{\"email\":\"nina@mrs.local\"}");
        ReflectionTestUtils.setField(row, "id", 9L);
        given(auditLogRepository.search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(row)));
        given(userRepository.findAllById(any())).willReturn(List.of(account(3L, "System Admin",
                "admin@mrs.local")));

        Page<AuditLogView> page = service.searchAudit(null, "  ", null, null, 0);

        AuditLogView view = page.getContent().get(0);
        assertThat(view.actorLabel()).isEqualTo("System Admin (admin@mrs.local)");
        assertThat(view.target()).isEqualTo("USER #42");
        assertThat(view.details()).contains("nina@mrs.local").contains("\n");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        then(auditLogRepository).should().search(isNull(), isNull(), isNull(), isNull(),
                pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(AdminLogService.PAGE_SIZE);
        assertThat(pageable.getValue().getSort().getOrderFor("timestamp").getDirection())
                .isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
    }

    @Test
    void searchAuditPassesInclusiveDateBoundsAsHalfOpenInstants() {
        given(auditLogRepository.search(eq(3L), eq(AuditLog.ACTION_SONG_EDIT),
                eq(LocalDateTime.of(2026, 1, 1, 0, 0)), eq(LocalDateTime.of(2026, 2, 1, 0, 0)),
                any(Pageable.class))).willReturn(Page.empty());

        service.searchAudit(3L, AuditLog.ACTION_SONG_EDIT, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31), 2);

        then(auditLogRepository).should().search(eq(3L), eq(AuditLog.ACTION_SONG_EDIT),
                eq(LocalDateTime.of(2026, 1, 1, 0, 0)), eq(LocalDateTime.of(2026, 2, 1, 0, 0)),
                any(Pageable.class));
    }

    @Test
    void missingActorFallsBackToANumericLabel() {
        AuditLog row = new AuditLog(99L, AuditLog.ACTION_SONG_DELETE, AuditLog.ENTITY_SONG, 12L,
                null);
        ReflectionTestUtils.setField(row, "id", 1L);
        given(auditLogRepository.search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(row)));
        given(userRepository.findAllById(any())).willReturn(List.of());

        assertThat(service.searchAudit(null, null, null, null, 0).getContent().get(0).actorLabel())
                .isEqualTo("User #99");
    }

    @Test
    void searchRecommendationsMapsLlmFilterAndOutcome() {
        RecommendationLog used = recommendation(1L, true, true, "upbeat");
        RecommendationLog fallback = recommendation(2L, true, false, "quiet");
        RecommendationLog keyword = recommendation(3L, false, null, "piano");
        given(recommendationLogRepository.search(eq(8L), eq(Boolean.TRUE), isNull(), isNull(),
                any(Pageable.class))).willReturn(new PageImpl<>(List.of(used)));
        given(recommendationLogRepository.search(isNull(), eq(Boolean.FALSE), isNull(), isNull(),
                any(Pageable.class))).willReturn(new PageImpl<>(List.of(fallback)));
        given(recommendationLogRepository.search(isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class))).willReturn(new PageImpl<>(List.of(keyword)));
        given(userRepository.findAllById(any())).willReturn(List.of(account(8L, "Dana",
                "dana@mrs.local")));

        assertThat(service.searchRecommendations(8L, "success", null, null, 0).getContent().get(0)
                .llmOutcome()).isEqualTo("Used");
        assertThat(service.searchRecommendations(null, "fallback", null, null, 0).getContent()
                .get(0).llmOutcome()).isEqualTo("Fallback");
        RecommendationLogView keywordView =
                service.searchRecommendations(null, "", null, null, 0).getContent().get(0);
        assertThat(keywordView.llmOutcome()).isEqualTo("Keyword");
        assertThat(keywordView.userLabel()).isEqualTo("Dana (dana@mrs.local)");
    }

    @Test
    void actionsMergesKnownConstantsWithDistinctValuesFromTheTable() {
        given(auditLogRepository.findDistinctActions()).willReturn(List.of("LEGACY_ACTION"));

        assertThat(service.actions()).contains(AuditLog.ACTION_USER_CREATE,
                AuditLog.ACTION_SONG_EDIT, "LEGACY_ACTION");
    }

    private static RecommendationLog recommendation(long id, boolean llmUsed, Boolean llmSucceeded,
            String query) {
        RecommendationLog row = new RecommendationLog(8L, query, "{\"moods\":[\"Calm\"]}", llmUsed,
                llmSucceeded, 4);
        ReflectionTestUtils.setField(row, "id", id);
        return row;
    }

    private static User account(long id, String name, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(name);
        user.setEmail(email);
        user.setPasswordHash("{noop}x");
        user.setRole(Role.CONTENT_DESIGNER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
