package com.rosogisoft.web;

import com.rosogisoft.service.I18nService;
import com.rosogisoft.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mocktail.deployment", name = "mode", havingValue = "database")
public class PasswordChangeController {

    private final UserService userService;
    private final CurrentUserHelper currentUserHelper;
    private final I18nService i18n;

    @GetMapping("/password/change")
    public String changeForm(Model model) {
        model.addAttribute("user", currentUserHelper.currentUser());
        return "password-change";
    }

    @PostMapping("/password/change")
    public String change(Authentication authentication,
                         @RequestParam String currentPassword,
                         @RequestParam String newPassword,
                         @RequestParam String confirmPassword,
                         RedirectAttributes ra) {
        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("errorMessage", i18n.t("password.confirmMismatch"));
            return "redirect:/password/change";
        }
        if (newPassword.length() < 12) {
            ra.addFlashAttribute("errorMessage", i18n.t("password.tooShort"));
            return "redirect:/password/change";
        }
        try {
            userService.changeOwnPassword(authentication.getName(), currentPassword, newPassword);
            ra.addFlashAttribute("successMessage", i18n.t("flash.passwordChanged"));
            return "redirect:/dashboard";
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", i18n.t("password.currentInvalid"));
            return "redirect:/password/change";
        }
    }
}
