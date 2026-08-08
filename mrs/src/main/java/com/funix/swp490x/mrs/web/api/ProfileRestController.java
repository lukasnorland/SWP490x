package com.funix.swp490x.mrs.web.api;

import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.web.Routes;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** P-05 profile REST stub. */
@RestController
@RequestMapping(path = Routes.API_PROFILE, produces = MediaType.APPLICATION_JSON_VALUE)
public class ProfileRestController {

    @GetMapping
    public Map<String, Object> profile(@AuthenticationPrincipal MrsUserDetails user) {
        return Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "displayName", user.getDisplayName(),
                "role", user.getRole().name(),
                "roleDisplayName", user.getRole().getDisplayName(),
                "playlistHistory", PageResponse.empty(0, 20));
    }
}
