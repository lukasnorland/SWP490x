package com.funix.swp490x.mrs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.funix.swp490x.mrs.domain.AuditLog;
import com.funix.swp490x.mrs.domain.SystemSetting;
import com.funix.swp490x.mrs.repository.AuditLogRepository;
import com.funix.swp490x.mrs.repository.SystemSettingRepository;
import com.funix.swp490x.mrs.settings.SettingKey;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private SystemSettingRepository settingRepository;
    @Mock
    private AuditLogRepository auditLogRepository;

    private SettingsService service;

    @BeforeEach
    void setUp() {
        service = new SettingsService(settingRepository, auditLogRepository);
        given(settingRepository.findAll()).willReturn(List.of());
    }

    @Test
    void emptyTableYieldsEnumDefaults() {
        assertThat(service.sessionInactivityHours()).isEqualTo(8);
        assertThat(service.lockoutThreshold()).isEqualTo(5);
        assertThat(service.llmModel()).isEqualTo("gemini-3.8-flash");
        assertThat(service.llmTimeout().toSeconds()).isEqualTo(30);
    }

    @Test
    void updateWritesOnlyChangedKeysAndAuditsBeforeAfter() {
        given(settingRepository.findBySettingKey(SettingKey.LOCKOUT_THRESHOLD.key()))
                .willReturn(java.util.Optional.empty());
        given(settingRepository.save(any(SystemSetting.class))).willAnswer(invocation -> {
            SystemSetting row = invocation.getArgument(0);
            ReflectionTestUtils.setField(row, "id", 41L);
            return row;
        });

        Map<SettingKey, String> submitted = defaults();
        submitted.put(SettingKey.LOCKOUT_THRESHOLD, "8");
        assertThat(service.update(submitted, 1L)).isEqualTo(1);

        ArgumentCaptor<SystemSetting> saved = ArgumentCaptor.forClass(SystemSetting.class);
        verify(settingRepository).save(saved.capture());
        assertThat(saved.getValue().getSettingKey()).isEqualTo("login.lockout.threshold");
        assertThat(saved.getValue().getSettingValue()).isEqualTo("8");

        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(AuditLog.ACTION_SETTINGS_UPDATE);
        assertThat(audit.getValue().getEntityType()).isEqualTo(AuditLog.ENTITY_SYSTEM_SETTING);
        assertThat(audit.getValue().getEntityId()).isEqualTo(41L);
        assertThat(audit.getValue().getDetails())
                .contains("\"key\":\"login.lockout.threshold\"")
                .contains("\"before\":\"5\"")
                .contains("\"after\":\"8\"");
    }

    @Test
    void outOfRangeValueRejectsTheWholeSubmit() {
        Map<SettingKey, String> submitted = defaults();
        submitted.put(SettingKey.LLM_TIMEOUT_SECONDS, "31");
        submitted.put(SettingKey.LOCKOUT_THRESHOLD, "8");

        assertThatThrownBy(() -> service.update(submitted, 1L))
                .isInstanceOf(InvalidSettingsException.class)
                .satisfies(thrown -> {
                    InvalidSettingsException error = (InvalidSettingsException) thrown;
                    assertThat(error.getErrors()).containsKey(SettingKey.LLM_TIMEOUT_SECONDS);
                    assertThat(error.getErrors().get(SettingKey.LLM_TIMEOUT_SECONDS))
                            .isEqualTo(SettingsService.message(SettingKey.LLM_TIMEOUT_SECONDS));
                });

        verify(settingRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void maxQueryCharsMustBeAtLeastMin() {
        Map<SettingKey, String> submitted = defaults();
        submitted.put(SettingKey.LLM_MIN_QUERY_CHARS, "40");
        submitted.put(SettingKey.LLM_MAX_QUERY_CHARS, "30");

        assertThatThrownBy(() -> service.update(submitted, 1L))
                .isInstanceOf(InvalidSettingsException.class);

        verify(settingRepository, never()).save(any());
    }

    @Test
    void unknownLlmModelIsRejected() {
        Map<SettingKey, String> submitted = defaults();
        submitted.put(SettingKey.LLM_MODEL, "not-a-gemini-model");

        assertThatThrownBy(() -> service.update(submitted, 1L))
                .isInstanceOf(InvalidSettingsException.class)
                .satisfies(thrown -> assertThat(((InvalidSettingsException) thrown).getErrors())
                        .containsEntry(SettingKey.LLM_MODEL, "Pick a listed Gemini model."));

        verify(settingRepository, never()).save(any());
    }

    @Test
    void unchangedSubmitWritesNothing() {
        assertThat(service.update(defaults(), 1L)).isZero();
        verify(settingRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void resetToDefaultsWritesChangedKeys() {
        SystemSetting stored = new SystemSetting(SettingKey.LOCKOUT_THRESHOLD.key(), "8");
        ReflectionTestUtils.setField(stored, "id", 41L);
        given(settingRepository.findAll()).willReturn(List.of(stored));
        given(settingRepository.findBySettingKey(SettingKey.LOCKOUT_THRESHOLD.key()))
                .willReturn(java.util.Optional.of(stored));
        given(settingRepository.save(any(SystemSetting.class))).willAnswer(invocation ->
                invocation.getArgument(0));

        assertThat(service.resetToDefaults(1L)).isEqualTo(1);

        ArgumentCaptor<SystemSetting> saved = ArgumentCaptor.forClass(SystemSetting.class);
        verify(settingRepository).save(saved.capture());
        assertThat(saved.getValue().getSettingValue())
                .isEqualTo(SettingKey.LOCKOUT_THRESHOLD.defaultValue());
    }

    @Test
    void resetWhenAlreadyDefaultWritesNothing() {
        assertThat(service.resetToDefaults(1L)).isZero();
        verify(settingRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
    }

    private static Map<SettingKey, String> defaults() {
        Map<SettingKey, String> values = new EnumMap<>(SettingKey.class);
        for (SettingKey key : SettingKey.values()) {
            values.put(key, key.defaultValue());
        }
        return values;
    }
}
