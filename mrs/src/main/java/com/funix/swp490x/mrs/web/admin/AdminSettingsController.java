package com.funix.swp490x.mrs.web.admin;

import com.funix.swp490x.mrs.security.MrsUserDetails;
import com.funix.swp490x.mrs.service.CatalogProviderNotFoundException;
import com.funix.swp490x.mrs.service.CatalogProviderService;
import com.funix.swp490x.mrs.service.CatalogProviderService.ProviderImpact;
import com.funix.swp490x.mrs.service.DuplicateCatalogProviderException;
import com.funix.swp490x.mrs.service.InvalidCatalogProviderException;
import com.funix.swp490x.mrs.service.InvalidSettingsException;
import com.funix.swp490x.mrs.service.ProviderDeleteNotConfirmedException;
import com.funix.swp490x.mrs.llm.GeminiModels;
import com.funix.swp490x.mrs.service.SettingsService;
import com.funix.swp490x.mrs.settings.SettingKey;
import com.funix.swp490x.mrs.web.Messages;
import com.funix.swp490x.mrs.web.Routes;
import jakarta.servlet.http.HttpServletResponse;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * P-06d — System Settings (UC-31). General and LLM save together; catalog
 * providers are their own create/delete actions.
 */
@Controller
public class AdminSettingsController {

    private final SettingsService settingsService;
    private final CatalogProviderService providerService;

    public AdminSettingsController(SettingsService settingsService,
            CatalogProviderService providerService) {
        this.settingsService = settingsService;
        this.providerService = providerService;
    }

    @GetMapping(Routes.ADMIN_SETTINGS)
    public String settings(Model model) {
        populate(model, settingsService.currentValues(), Map.of());
        return "admin/settings";
    }

    @PostMapping(Routes.ADMIN_SETTINGS_SAVE)
    public String save(@RequestParam Map<String, String> form,
            @AuthenticationPrincipal MrsUserDetails user,
            Model model,
            RedirectAttributes redirectAttributes,
            HttpServletResponse response) {

        Map<SettingKey, String> submitted = submittedFrom(form);
        try {
            int changed = settingsService.update(submitted, user.getId());
            settingsActionFlash(redirectAttributes, changed > 0
                    ? Messages.SETTINGS_SAVED
                    : Messages.SETTINGS_UNCHANGED,
                    changed > 0 ? "success" : "info");
            return redirectToSettingsActions();
        } catch (InvalidSettingsException e) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            model.addAttribute("flashVariant", "danger");
            model.addAttribute("flash", Messages.SETTINGS_NOT_SAVED);
            model.addAttribute("flashPlacement", "actions");
            populate(model, submitted, e.getErrors());
            return "admin/settings";
        }
    }

    @PostMapping(Routes.ADMIN_SETTINGS_RESET)
    public String reset(@AuthenticationPrincipal MrsUserDetails user,
            RedirectAttributes redirectAttributes) {
        int changed = settingsService.resetToDefaults(user.getId());
        settingsActionFlash(redirectAttributes, changed > 0
                ? Messages.SETTINGS_RESET
                : Messages.SETTINGS_ALREADY_DEFAULT,
                changed > 0 ? "success" : "info");
        return redirectToSettingsActions();
    }

    @PostMapping(Routes.ADMIN_SETTINGS_PROVIDERS)
    public String createProvider(@RequestParam String name,
            @RequestParam String slug,
            @AuthenticationPrincipal MrsUserDetails user,
            RedirectAttributes redirectAttributes) {

        try {
            providerService.create(name, slug, user.getId());
        } catch (InvalidCatalogProviderException | DuplicateCatalogProviderException e) {
            redirectAttributes.addFlashAttribute("flashVariant", "danger");
            redirectAttributes.addFlashAttribute("flash", e.getMessage());
            redirectAttributes.addFlashAttribute("submittedProviderName", name);
            redirectAttributes.addFlashAttribute("submittedProviderSlug", slug);
            return "redirect:" + Routes.ADMIN_SETTINGS;
        }
        redirectAttributes.addFlashAttribute("flashVariant", "success");
        redirectAttributes.addFlashAttribute("flash", Messages.PROVIDER_CREATED);
        return "redirect:" + Routes.ADMIN_SETTINGS;
    }

    @PostMapping(Routes.ADMIN_SETTINGS_PROVIDER_DELETE)
    public String deleteProvider(@PathVariable Long id,
            @RequestParam String confirmName,
            @AuthenticationPrincipal MrsUserDetails user,
            RedirectAttributes redirectAttributes) {

        try {
            ProviderImpact removed = providerService.delete(id, confirmName, user.getId());
            redirectAttributes.addFlashAttribute("flashVariant", "success");
            redirectAttributes.addFlashAttribute("flash",
                    Messages.providerDeleted(removed.provider().getName(), removed.songCount()));
        } catch (ProviderDeleteNotConfirmedException e) {
            redirectAttributes.addFlashAttribute("flashVariant", "danger");
            redirectAttributes.addFlashAttribute("flash", Messages.PROVIDER_DELETE_CONFIRM);
        } catch (CatalogProviderNotFoundException e) {
            redirectAttributes.addFlashAttribute("flashVariant", "danger");
            redirectAttributes.addFlashAttribute("flash", Messages.PROVIDER_NOT_FOUND);
        }
        return "redirect:" + Routes.ADMIN_SETTINGS;
    }

    private void populate(Model model, Map<SettingKey, String> values,
            Map<SettingKey, String> errors) {
        model.addAttribute("pageTitle", "System Settings");
        model.addAttribute("activeNav", "admin-settings");
        Map<String, String> byKey = new LinkedHashMap<>();
        for (SettingKey key : SettingKey.values()) {
            byKey.put(key.key(), values.getOrDefault(key, key.defaultValue()));
        }
        Map<String, String> errorByKey = new LinkedHashMap<>();
        errors.forEach((key, message) -> errorByKey.put(key.key(), message));
        model.addAttribute("setting", byKey);
        model.addAttribute("settingErrors", errorByKey);
        model.addAttribute("llmModels", GeminiModels.forForm(byKey.get(SettingKey.LLM_MODEL.key())));
        model.addAttribute("providers", providerService.listWithImpact());
    }

    private static String redirectToSettingsActions() {
        return "redirect:" + Routes.ADMIN_SETTINGS + "#settings-actions";
    }

    private static void settingsActionFlash(RedirectAttributes redirectAttributes,
            String message, String variant) {
        redirectAttributes.addFlashAttribute("flashVariant", variant);
        redirectAttributes.addFlashAttribute("flash", message);
        redirectAttributes.addFlashAttribute("flashPlacement", "actions");
    }

    private static Map<SettingKey, String> submittedFrom(Map<String, String> form) {
        Map<SettingKey, String> submitted = new EnumMap<>(SettingKey.class);
        for (SettingKey key : SettingKey.values()) {
            String raw = form.get(key.key());
            submitted.put(key, raw == null ? "" : raw.trim());
        }
        return submitted;
    }
}
