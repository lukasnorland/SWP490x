package com.funix.swp490x.mrs.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** P-05 My Profile & Playlist History (FT-02). */
@Controller
public class ProfileController {

    @GetMapping(Routes.PROFILE)
    public String profile(Model model) {
        model.addAttribute("pageTitle", "My Profile");
        model.addAttribute("activeNav", "profile");
        return "profile/index";
    }
}
