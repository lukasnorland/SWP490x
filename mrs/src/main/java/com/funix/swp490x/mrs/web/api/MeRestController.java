package com.funix.swp490x.mrs.web.api;

import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.web.Routes;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Current session identity. */
@RestController
@RequestMapping(path = Routes.API_ME, produces = MediaType.APPLICATION_JSON_VALUE)
public class MeRestController {

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal MrsUserDetails user) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.getRole().getDisplayName(),
                user.isMustChangePassword(),
                user.getRole().getLandingPath());
    }
}
