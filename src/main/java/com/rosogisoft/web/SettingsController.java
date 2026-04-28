package com.rosogisoft.web;

import com.rosogisoft.domain.SettingKey;
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

    @GetMapping
    public String settings(Model model) {
        var user     = currentUserHelper.currentUser();
        var settings = settingsService.getSettings(user);
        model.addAttribute("user",     user);
        model.addAttribute("settings", settings.asStringMap());
        model.addAttribute("keys",     SettingKey.values());
        return "settings/index";
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
        ra.addFlashAttribute("successMessage", "Settings saved.");
        return "redirect:/settings";
    }
}
