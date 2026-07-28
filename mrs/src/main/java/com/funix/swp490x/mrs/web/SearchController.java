package com.funix.swp490x.mrs.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** P-02 Search & Recommendation (FT-03, FT-04, FT-05). */
@Controller
public class SearchController {

    @GetMapping(Routes.SEARCH)
    public String search(Model model) {
        model.addAttribute("pageTitle", "Search & Recommendation");
        model.addAttribute("activeNav", "search");
        return "search/index";
    }
}
