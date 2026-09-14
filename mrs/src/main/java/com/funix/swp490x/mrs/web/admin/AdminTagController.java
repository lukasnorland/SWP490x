package com.funix.swp490x.mrs.web.admin;

import com.funix.swp490x.mrs.domain.TagType;
import com.funix.swp490x.mrs.service.TagVocabularyException;
import com.funix.swp490x.mrs.service.TagVocabularyService;
import com.funix.swp490x.mrs.service.TagVocabularyService.TagRow;
import com.funix.swp490x.mrs.web.Messages;
import com.funix.swp490x.mrs.web.Routes;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * P-06b Tags tab — dictionary CRUD for unused names. In-use names stay on
 * songs (and therefore on staged JSON); this screen will not rename or delete
 * those.
 */
@Controller
public class AdminTagController {

    private final TagVocabularyService tagVocabularyService;

    public AdminTagController(TagVocabularyService tagVocabularyService) {
        this.tagVocabularyService = tagVocabularyService;
    }

    @GetMapping(Routes.ADMIN_CATALOG_TAGS)
    public String tags(Model model) {
        populate(model);
        return "admin/tags";
    }

    @PostMapping(Routes.ADMIN_CATALOG_TAGS)
    public String create(@RequestParam TagType type,
            @RequestParam String name,
            RedirectAttributes redirectAttributes) {
        try {
            tagVocabularyService.create(type, name);
            flash(redirectAttributes, "success", Messages.TAG_CREATED);
        } catch (TagVocabularyException e) {
            flash(redirectAttributes, "danger", e.getMessage());
        }
        return "redirect:" + Routes.ADMIN_CATALOG_TAGS;
    }

    @PostMapping(Routes.ADMIN_CATALOG_TAG_RENAME)
    public String rename(@PathVariable Long id,
            @RequestParam String name,
            RedirectAttributes redirectAttributes) {
        try {
            tagVocabularyService.rename(id, name);
            flash(redirectAttributes, "success", Messages.TAG_RENAMED);
        } catch (TagVocabularyException e) {
            flash(redirectAttributes, "danger", e.getMessage());
        }
        return "redirect:" + Routes.ADMIN_CATALOG_TAGS;
    }

    @PostMapping(Routes.ADMIN_CATALOG_TAG_DELETE)
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            tagVocabularyService.delete(id);
            flash(redirectAttributes, "success", Messages.TAG_DELETED);
        } catch (TagVocabularyException e) {
            flash(redirectAttributes, "danger", e.getMessage());
        }
        return "redirect:" + Routes.ADMIN_CATALOG_TAGS;
    }

    private void populate(Model model) {
        model.addAttribute("pageTitle", "Tag dictionary");
        model.addAttribute("activeNav", "admin-catalog");
        model.addAttribute("catalogTab", "tags");
        model.addAttribute("tagTypes", TagType.values());

        // One section per vocabulary, in TagType order, so an empty vocabulary
        // still shows its heading and Add form.
        List<TagRow> rows = tagVocabularyService.list();
        model.addAttribute("tagGroups", Arrays.stream(TagType.values())
                .map(type -> new TagGroup(type,
                        rows.stream().filter(row -> row.type() == type).toList()))
                .toList());
    }

    private void flash(RedirectAttributes redirectAttributes, String variant, String message) {
        redirectAttributes.addFlashAttribute("flash", message);
        redirectAttributes.addFlashAttribute("flashVariant", variant);
    }

    public record TagGroup(TagType type, List<TagRow> rows) {
    }
}
