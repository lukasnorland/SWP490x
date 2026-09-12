package com.funix.swp490x.mrs.service;

import com.funix.swp490x.mrs.settings.SettingKey;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * UC-31 E2: at least one submitted value is outside its permitted range, so
 * nothing is saved (HTTP 422, MSG_026).
 */
public class InvalidSettingsException extends RuntimeException {

    private final Map<SettingKey, String> errors;

    public InvalidSettingsException(Map<SettingKey, String> errors) {
        super("One or more settings are outside their permitted range");
        this.errors = Map.copyOf(new LinkedHashMap<>(errors));
    }

    public Map<SettingKey, String> getErrors() {
        return errors;
    }
}
