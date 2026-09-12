package com.funix.swp490x.mrs.settings;

/**
 * Every operational parameter P-06d can change (UC-31). Defaults and ranges
 * live here so validation and the form stay on one list (MSG_026, BR-17).
 */
public enum SettingKey {

    SESSION_INACTIVITY_HOURS(
            "session.inactivity.hours", Kind.INT, "8", 1, 8,
            "Session inactivity window"),
    LOCKOUT_THRESHOLD(
            "login.lockout.threshold", Kind.INT, "5", 1, 20,
            "Login lockout threshold"),
    LOCKOUT_WINDOW_MINUTES(
            "login.lockout.window.minutes", Kind.INT, "15", 1, 60,
            "Login lockout window"),
    RESET_LINK_MINUTES(
            "password.reset.validity.minutes", Kind.INT, "30", 5, 120,
            "Password-reset link validity"),
    LLM_MODEL(
            "llm.model", Kind.STRING, "gemini-3.8-flash", null, null,
            "LLM model"),
    LLM_TIMEOUT_SECONDS(
            "llm.timeout.seconds", Kind.INT, "30", 1, 30,
            "LLM interpretation timeout"),
    LLM_MIN_QUERY_CHARS(
            "llm.min.query.chars", Kind.INT, "10", 1, 50,
            "Minimum query characters"),
    LLM_MAX_QUERY_CHARS(
            "llm.max.query.chars", Kind.INT, "200", 50, 500,
            "Maximum query characters");

    public enum Kind {
        INT,
        STRING
    }

    private final String key;
    private final Kind kind;
    private final String defaultValue;
    private final Integer min;
    private final Integer max;
    private final String label;

    SettingKey(String key, Kind kind, String defaultValue, Integer min, Integer max, String label) {
        this.key = key;
        this.kind = kind;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public Kind kind() {
        return kind;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public Integer min() {
        return min;
    }

    public Integer max() {
        return max;
    }

    public String label() {
        return label;
    }

    public int defaultInt() {
        return Integer.parseInt(defaultValue);
    }

    public static SettingKey byKey(String key) {
        for (SettingKey setting : values()) {
            if (setting.key.equals(key)) {
                return setting;
            }
        }
        throw new IllegalArgumentException("Unknown setting: " + key);
    }
}
