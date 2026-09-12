package com.funix.swp490x.mrs.service;

import com.funix.swp490x.mrs.domain.AuditLog;
import com.funix.swp490x.mrs.domain.SystemSetting;
import com.funix.swp490x.mrs.llm.GeminiModels;
import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.SystemSettingRepository;
import com.funix.swp490x.mrs.settings.SettingKey;
import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Runtime values for P-06d (UC-31). Rows in {@code system_setting} override
 * the defaults on {@link SettingKey}; an empty table means every default.
 *
 * <p>The in-process map is dropped on every save so a change is visible on
 * the next request (BR-17). A 5-minute ceiling stops a quiet database from
 * being hit on every login check.
 */
@Service
public class SettingsService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final SystemSettingRepository settingRepository;
    private final AuditLogRepository auditLogRepository;

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
    private volatile long cacheLoadedAtMillis = 0L;

    public SettingsService(SystemSettingRepository settingRepository,
            AuditLogRepository auditLogRepository) {
        this.settingRepository = settingRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public int sessionInactivityHours() {
        return intValue(SettingKey.SESSION_INACTIVITY_HOURS);
    }

    public Duration sessionInactivity() {
        return Duration.ofHours(sessionInactivityHours());
    }

    public int lockoutThreshold() {
        return intValue(SettingKey.LOCKOUT_THRESHOLD);
    }

    public Duration lockoutWindow() {
        return Duration.ofMinutes(intValue(SettingKey.LOCKOUT_WINDOW_MINUTES));
    }

    public Duration resetLinkValidity() {
        return Duration.ofMinutes(intValue(SettingKey.RESET_LINK_MINUTES));
    }

    public String llmModel() {
        return value(SettingKey.LLM_MODEL);
    }

    public Duration llmTimeout() {
        return Duration.ofSeconds(intValue(SettingKey.LLM_TIMEOUT_SECONDS));
    }

    public int llmMinQueryChars() {
        return intValue(SettingKey.LLM_MIN_QUERY_CHARS);
    }

    public int llmMaxQueryChars() {
        return intValue(SettingKey.LLM_MAX_QUERY_CHARS);
    }

    public Map<SettingKey, String> currentValues() {
        Map<SettingKey, String> values = new EnumMap<>(SettingKey.class);
        for (SettingKey key : SettingKey.values()) {
            values.put(key, value(key));
        }
        return values;
    }

    /**
     * Validates every submitted value, then writes only those that changed.
     * One out-of-range field rejects the whole submit (UC-31 E2).
     *
     * @return how many keys actually changed
     */
    @Transactional
    public int update(Map<SettingKey, String> submitted, Long actorId) {
        Map<SettingKey, String> current = currentValues();
        Map<SettingKey, String> errors = validate(submitted);
        if (!errors.isEmpty()) {
            throw new InvalidSettingsException(errors);
        }
        int changed = 0;
        for (SettingKey key : SettingKey.values()) {
            String next = submitted.get(key);
            if (next == null) {
                continue;
            }
            if (writeIfChanged(key, current.get(key), next, actorId)) {
                changed++;
            }
        }
        evictCache();
        return changed;
    }

    /** Restores every P-06d key to {@link SettingKey#defaultValue()}. */
    @Transactional
    public int resetToDefaults(Long actorId) {
        Map<SettingKey, String> current = currentValues();
        int changed = 0;
        for (SettingKey key : SettingKey.values()) {
            if (writeIfChanged(key, current.get(key), key.defaultValue(), actorId)) {
                changed++;
            }
        }
        evictCache();
        return changed;
    }

    private boolean writeIfChanged(SettingKey key, String before, String next, Long actorId) {
        if (Objects.equals(before, next)) {
            return false;
        }
        SystemSetting row = settingRepository.findBySettingKey(key.key())
                .orElseGet(() -> new SystemSetting(key.key(), next));
        row.setSettingValue(next);
        row.touch(actorId);
        SystemSetting saved = settingRepository.save(row);
        auditLogRepository.save(new AuditLog(actorId,
                AuditLog.ACTION_SETTINGS_UPDATE,
                AuditLog.ENTITY_SYSTEM_SETTING,
                saved.getId(),
                detailsJson(key.key(), before, next)));
        return true;
    }

    private void evictCache() {
        cache.clear();
        cacheLoadedAtMillis = 0L;
    }

    private Map<SettingKey, String> validate(Map<SettingKey, String> submitted) {
        Map<SettingKey, String> errors = new LinkedHashMap<>();
        Integer minChars = null;
        Integer maxChars = null;
        for (SettingKey key : SettingKey.values()) {
            String raw = submitted.get(key);
            if (raw == null) {
                continue;
            }
            if (key.kind() == SettingKey.Kind.STRING) {
                if (!StringUtils.hasText(raw) || raw.length() > 255) {
                    errors.put(key, key.label() + " is required.");
                } else if (key == SettingKey.LLM_MODEL && !GeminiModels.isKnown(raw)) {
                    errors.put(key, "Pick a listed Gemini model.");
                }
                continue;
            }
            Integer parsed = parseInt(raw);
            if (parsed == null) {
                errors.put(key, message(key));
                continue;
            }
            if (key.min() != null && parsed < key.min()
                    || key.max() != null && parsed > key.max()) {
                errors.put(key, message(key));
            }
            if (key == SettingKey.LLM_MIN_QUERY_CHARS) {
                minChars = parsed;
            }
            if (key == SettingKey.LLM_MAX_QUERY_CHARS) {
                maxChars = parsed;
            }
        }
        if (minChars != null && maxChars != null && maxChars < minChars) {
            errors.put(SettingKey.LLM_MAX_QUERY_CHARS,
                    SettingKey.LLM_MAX_QUERY_CHARS.label()
                            + " must be greater than or equal to "
                            + SettingKey.LLM_MIN_QUERY_CHARS.label() + ".");
        }
        return errors;
    }

    public static String message(SettingKey key) {
        if (key.min() != null && key.max() != null) {
            return key.label() + " must be between " + key.min() + " and " + key.max() + ".";
        }
        return key.label() + " is not valid.";
    }

    private int intValue(SettingKey key) {
        return Integer.parseInt(value(key));
    }

    private String value(SettingKey key) {
        refreshCache();
        String stored = cache.get(key.key());
        return stored != null ? stored : key.defaultValue();
    }

    private void refreshCache() {
        long loaded = cacheLoadedAtMillis;
        if (loaded != 0L && System.currentTimeMillis() - loaded < CACHE_TTL.toMillis()) {
            return;
        }
        synchronized (this) {
            if (cacheLoadedAtMillis != 0L
                    && System.currentTimeMillis() - cacheLoadedAtMillis < CACHE_TTL.toMillis()) {
                return;
            }
            cache.clear();
            for (SystemSetting row : settingRepository.findAll()) {
                cache.put(row.getSettingKey(), row.getSettingValue());
            }
            cacheLoadedAtMillis = System.currentTimeMillis();
        }
    }

    private static Integer parseInt(String raw) {
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String detailsJson(String key, String before, String after) {
        return "{\"key\":%s,\"before\":%s,\"after\":%s}".formatted(
                jsonString(key), jsonString(before), jsonString(after));
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
