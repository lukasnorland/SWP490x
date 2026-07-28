package com.funix.swp490x.mrs.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** P-03a My Playlists and P-03b Playlist Detail / Editor (FT-06 – FT-08). */
@Controller
public class PlaylistController {

    @GetMapping(Routes.PLAYLISTS)
    public String list(Model model) {
        model.addAttribute("pageTitle", "My Playlists");
        model.addAttribute("activeNav", "playlists");
        return "playlist/list";
    }

    @GetMapping(Routes.PLAYLISTS + "/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("pageTitle", "Playlist detail");
        model.addAttribute("activeNav", "playlists");
        model.addAttribute("breadcrumbParent", "My Playlists");
        model.addAttribute("breadcrumbParentUrl", Routes.PLAYLISTS);
        model.addAttribute("playlistId", id);
        return "playlist/detail";
    }
}
