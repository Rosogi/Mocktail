package com.rosogisoft.web.admin;

import com.rosogisoft.service.I18nService;
import com.rosogisoft.service.UserService;
import com.rosogisoft.web.CurrentUserHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mocktail.deployment", name = "mode", havingValue = "database")
public class AdminUserController {

    private final UserService userService;
    private final I18nService i18n;
    private final CurrentUserHelper currentUserHelper;

    @GetMapping
    public String users(Model model) {
        model.addAttribute("user", currentUserHelper.currentUser());
        model.addAttribute("accounts", userService.findDatabaseAccounts());
        model.addAttribute("roles", userService.findRoles());
        return "admin/users";
    }

    @PostMapping
    public String create(@RequestParam String login,
                         @RequestParam(required = false) String displayName,
                         @RequestParam(defaultValue = UserService.ROLE_USER) String roleCode,
                         RedirectAttributes ra) {
        try {
            UserService.OneTimePassword created = userService.createDatabaseUser(login, displayName, roleCode);
            ra.addFlashAttribute("successMessage", i18n.t("flash.userCreated", created.login()));
            ra.addFlashAttribute("generatedPassword", created.password());
            ra.addFlashAttribute("generatedPasswordLogin", created.login());
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable Long id, RedirectAttributes ra) {
        try {
            UserService.OneTimePassword reset = userService.resetPassword(id);
            ra.addFlashAttribute("successMessage", i18n.t("flash.passwordReset", reset.login()));
            ra.addFlashAttribute("generatedPassword", reset.password());
            ra.addFlashAttribute("generatedPasswordLogin", reset.login());
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/enabled")
    public String setEnabled(@PathVariable Long id,
                             @RequestParam boolean enabled,
                             RedirectAttributes ra) {
        try {
            userService.setEnabled(id, enabled);
            ra.addFlashAttribute("successMessage", i18n.t("flash.userUpdated"));
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/role")
    public String changeRole(@PathVariable Long id,
                             @RequestParam String roleCode,
                             RedirectAttributes ra) {
        try {
            userService.changeRole(id, roleCode);
            ra.addFlashAttribute("successMessage", i18n.t("flash.userUpdated"));
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }
}
