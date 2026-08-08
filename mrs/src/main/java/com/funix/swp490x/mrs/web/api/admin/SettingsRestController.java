package com.funix.swp490x.mrs.web.api.admin;

import com.funix.swp490x.mrs.web.Routes;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** P-06d settings REST stub. */
@RestController
@RequestMapping(path = Routes.API_ADMIN_SETTINGS, produces = MediaType.APPLICATION_JSON_VALUE)
public class SettingsRestController {

    @GetMapping
    public Map<String, Object> settings() {
        return Map.of(
                "llm", Map.of("configured", false),
                "security", Map.of(
                        "sessionTimeoutHours", 8,
                        "lockoutThreshold", 5,
                        "lockoutWindowMinutes", 15,
                        "resetLinkMinutes", 30),
                "message", "Settings API is scaffolded; editable values arrive with P-06d.");
    }
}
