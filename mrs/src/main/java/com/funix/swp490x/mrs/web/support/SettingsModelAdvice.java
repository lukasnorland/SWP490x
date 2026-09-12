package com.funix.swp490x.mrs.web.support;

import com.funix.swp490x.mrs.service.SettingsService;
import com.funix.swp490x.mrs.settings.SettingKey;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Puts the live P-06d values onto every model so login and reset copy never
 * hard-code BV-01 / BV-06 / BV-07.
 */
@ControllerAdvice
public class SettingsModelAdvice {

    private final SettingsService settings;

    public SettingsModelAdvice(ObjectProvider<SettingsService> settings) {
        this.settings = settings.getIfAvailable();
    }

    @ModelAttribute("sessionInactivityHours")
    public int sessionInactivityHours() {
        return settings != null
                ? settings.sessionInactivityHours()
                : SettingKey.SESSION_INACTIVITY_HOURS.defaultInt();
    }

    @ModelAttribute("lockoutWindowMinutes")
    public int lockoutWindowMinutes() {
        return settings != null
                ? (int) settings.lockoutWindow().toMinutes()
                : SettingKey.LOCKOUT_WINDOW_MINUTES.defaultInt();
    }

    @ModelAttribute("resetValidityMinutes")
    public int resetValidityMinutes() {
        return settings != null
                ? (int) settings.resetLinkValidity().toMinutes()
                : SettingKey.RESET_LINK_MINUTES.defaultInt();
    }
}
