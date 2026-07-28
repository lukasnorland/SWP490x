package com.funix.swp490x.mrs.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** P-04a Shared Workspace and P-04b Published Playlist View (FT-07, FT-08). */
@Controller
public class WorkspaceController {

    @GetMapping(Routes.WORKSPACE)
    public String list(Model model) {
        model.addAttribute("pageTitle", "Shared Workspace");
        model.addAttribute("activeNav", "workspace");
        return "workspace/list";
    }

    @GetMapping(Routes.WORKSPACE + "/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("pageTitle", "Published playlist");
        model.addAttribute("activeNav", "workspace");
        model.addAttribute("breadcrumbParent", "Shared Workspace");
        model.addAttribute("breadcrumbParentUrl", Routes.WORKSPACE);
        model.addAttribute("playlistId", id);
        return "workspace/detail";
    }
}
