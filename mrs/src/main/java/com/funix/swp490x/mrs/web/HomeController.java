package com.funix.swp490x.mrs.web;

import com.funix.swp490x.mrs.security.MrsUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    /** The root has no screen of its own; it forwards to the role landing page (2.1). */
    @GetMapping("/")
    public String home(@AuthenticationPrincipal MrsUserDetails user) {
        return "redirect:" + (user == null ? Routes.LOGIN : user.getRole().getLandingPath());
    }
}
