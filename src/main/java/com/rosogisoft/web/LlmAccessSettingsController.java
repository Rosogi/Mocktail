package com.rosogisoft.web;

import com.rosogisoft.config.ApplicationCapabilities;
import com.rosogisoft.domain.LlmAccessLevel;
import com.rosogisoft.service.I18nService;
import com.rosogisoft.service.LlmAccessTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings/llm-access")
@RequiredArgsConstructor
public class LlmAccessSettingsController {

    private final LlmAccessTokenService tokenService;
    private final CurrentUserHelper currentUserHelper;
    private final I18nService i18n;
    private final ApplicationCapabilities capabilities;

    @PostMapping("/generate")
    public String generate(RedirectAttributes ra) {
        ensureMcpEnabled();
        var user = currentUserHelper.currentUser();
        boolean hadToken = tokenService.findForUser(user).isPresent();
        var generated = tokenService.generateOrRegenerate(user);
        ra.addFlashAttribute("llmPlainToken", generated.plainToken());
        ra.addFlashAttribute("successMessage", i18n.t(
                hadToken ? "flash.llmTokenRegenerated" : "flash.llmTokenGenerated"));
        return "redirect:/settings";
    }

    @PostMapping("/permissions")
    public String permissions(@RequestParam LlmAccessLevel requestLogsAccess,
                              @RequestParam LlmAccessLevel mocksAccess,
                              RedirectAttributes ra) {
        ensureMcpEnabled();
        var user = currentUserHelper.currentUser();
        try {
            tokenService.updatePermissions(user, requestLogsAccess, mocksAccess);
            ra.addFlashAttribute("successMessage", i18n.t("flash.llmPermissionsSaved"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("errorMessage", i18n.t("flash.llmPermissionsFailed", e.getMessage()));
        }
        return "redirect:/settings";
    }

    private void ensureMcpEnabled() {
        if (!capabilities.isMcp()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
