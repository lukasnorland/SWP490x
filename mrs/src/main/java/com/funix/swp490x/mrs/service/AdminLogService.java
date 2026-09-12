package com.funix.swp490x.mrs.service;

import com.funix.swp490x.mrs.domain.AuditLog;
import com.funix.swp490x.mrs.domain.RecommendationLog;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.RecommendationLogRepository;
import com.funix.swp490x.mrs.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Read model for P-06e (UC-32). Writes stay on the services that change state.
 */
@Service
public class AdminLogService {

    public static final int PAGE_SIZE = 20;

    public static final String TAB_AUDIT = "audit";

    public static final String TAB_RECOMMENDATION = "recommendation";

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final AuditLogRepository auditLogRepository;
    private final RecommendationLogRepository recommendationLogRepository;
    private final UserRepository userRepository;

    public AdminLogService(AuditLogRepository auditLogRepository,
            RecommendationLogRepository recommendationLogRepository,
            UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.recommendationLogRepository = recommendationLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<AuditLogView> searchAudit(Long actorId, String action, LocalDate from,
            LocalDate to, int page) {
        Page<AuditLog> rows = auditLogRepository.search(actorId, blankToNull(action),
                startOf(from), startOfNext(to), pageable(page, "timestamp"));
        Map<Long, User> people = usersById(rows.stream().map(AuditLog::getActorId).toList());
        return rows.map(row -> toAuditView(row, people.get(row.getActorId())));
    }

    @Transactional(readOnly = true)
    public Page<RecommendationLogView> searchRecommendations(Long userId, String llm,
            LocalDate from, LocalDate to, int page) {
        Page<RecommendationLog> rows = recommendationLogRepository.search(userId,
                llmSucceeded(llm), startOf(from), startOfNext(to), pageable(page, "createdAt"));
        Map<Long, User> people = usersById(rows.stream().map(RecommendationLog::getUserId).toList());
        return rows.map(row -> toRecommendationView(row, people.get(row.getUserId())));
    }

    @Transactional(readOnly = true)
    public List<LogPerson> actors() {
        return people(auditLogRepository.findDistinctActorIds());
    }

    @Transactional(readOnly = true)
    public List<LogPerson> searchUsers() {
        return people(recommendationLogRepository.findDistinctUserIds());
    }

    @Transactional(readOnly = true)
    public List<String> actions() {
        Set<String> values = new LinkedHashSet<>(AuditLog.knownActions());
        values.addAll(auditLogRepository.findDistinctActions());
        List<String> ordered = new ArrayList<>(values);
        ordered.sort(String::compareTo);
        return ordered;
    }

    public static boolean isRecommendationTab(String tab) {
        return TAB_RECOMMENDATION.equalsIgnoreCase(tab);
    }

    private List<LogPerson> people(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, User> byId = usersById(ids);
        List<LogPerson> result = new ArrayList<>();
        for (Long id : ids) {
            result.add(new LogPerson(id, label(byId.get(id), id)));
        }
        result.sort((left, right) -> left.label().compareToIgnoreCase(right.label()));
        return result;
    }

    private Map<Long, User> usersById(List<Long> ids) {
        Set<Long> unique = ids.stream().filter(id -> id != null).collect(Collectors.toSet());
        if (unique.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(unique).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static AuditLogView toAuditView(AuditLog row, User actor) {
        return new AuditLogView(
                row.getId(),
                row.getTimestamp(),
                row.getActorId(),
                label(actor, row.getActorId()),
                row.getAction(),
                row.getEntityType(),
                row.getEntityId(),
                row.getEntityType() + " #" + row.getEntityId(),
                prettyJson(row.getDetails()));
    }

    private static RecommendationLogView toRecommendationView(RecommendationLog row, User user) {
        return new RecommendationLogView(
                row.getId(),
                row.getCreatedAt(),
                row.getUserId(),
                label(user, row.getUserId()),
                row.getQueryText(),
                prettyJson(row.getInterpretedFilters()),
                llmOutcome(row),
                row.getResultCount());
    }

    private static String llmOutcome(RecommendationLog row) {
        if (Boolean.TRUE.equals(row.getLlmSucceeded())) {
            return "Used";
        }
        if (Boolean.FALSE.equals(row.getLlmSucceeded())) {
            return "Fallback";
        }
        return "Keyword";
    }

    private static Boolean llmSucceeded(String llm) {
        if ("success".equalsIgnoreCase(llm)) {
            return Boolean.TRUE;
        }
        if ("fallback".equalsIgnoreCase(llm)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static String label(User user, Long id) {
        if (user == null) {
            return "User #" + id;
        }
        return user.getUsername() + " (" + user.getEmail() + ")";
    }

    private static String prettyJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(JSON.readTree(raw));
        } catch (RuntimeException e) {
            return raw;
        }
    }

    private static PageRequest pageable(int page, String sortProperty) {
        return PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, sortProperty));
    }

    private static LocalDateTime startOf(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private static LocalDateTime startOfNext(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay();
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record LogPerson(Long id, String label) {
    }

    public record AuditLogView(
            Long id,
            LocalDateTime timestamp,
            Long actorId,
            String actorLabel,
            String action,
            String entityType,
            Long entityId,
            String target,
            String details) {
    }

    public record RecommendationLogView(
            Long id,
            LocalDateTime createdAt,
            Long userId,
            String userLabel,
            String queryText,
            String filters,
            String llmOutcome,
            int resultCount) {
    }
}
