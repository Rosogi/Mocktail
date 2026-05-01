package com.rosogisoft.web;

import com.rosogisoft.domain.SettingKey;
import com.rosogisoft.service.I18nService;
import com.rosogisoft.service.UserSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final UserSettingsService settingsService;
    private final CurrentUserHelper   currentUserHelper;
    private final I18nService         i18n;

    @GetMapping
    public String settings(Model model) {
        var user     = currentUserHelper.currentUser();
        var settings = settingsService.getSettings(user);
        model.addAttribute("user",     user);
        model.addAttribute("settings", settings.asStringMap());
        model.addAttribute("keys",     SettingKey.values());
        model.addAttribute("languages", i18n.supportedLanguages());
        return "settings/index";
    }

    @PostMapping("/language")
    public String saveLanguage(@RequestParam String language,
                               RedirectAttributes ra) {
        var user = currentUserHelper.currentUser();
        String normalized = i18n.normalizeLanguage(language);
        settingsService.set(user, SettingKey.LANGUAGE, normalized);
        ra.addFlashAttribute("successMessage",
                i18n.tForLanguage(normalized, "flash.settingsSaved"));
        return "redirect:/settings";
    }

    @PostMapping("/default-response")
    public String saveDefaultResponse(
            @RequestParam String defaultResponseStatus,
            @RequestParam String defaultResponseBody,
            @RequestParam String defaultResponseCt,
            RedirectAttributes ra) {
        var user = currentUserHelper.currentUser();
        settingsService.setAll(user, Map.of(
                SettingKey.DEFAULT_RESPONSE_STATUS, defaultResponseStatus,
                SettingKey.DEFAULT_RESPONSE_BODY,   defaultResponseBody,
                SettingKey.DEFAULT_RESPONSE_CT,     defaultResponseCt
        ));
        ra.addFlashAttribute("successMessage", i18n.t("flash.settingsSaved"));
        return "redirect:/settings";
    }
}
