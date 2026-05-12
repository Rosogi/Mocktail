package com.rosogisoft.web;

import com.rosogisoft.domain.User;
import com.rosogisoft.service.EnvironmentService;
import com.rosogisoft.service.I18nService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/environments")
@RequiredArgsConstructor
public class EnvironmentController {

    private final CurrentUserHelper currentUserHelper;
    private final EnvironmentService environmentService;
    private final I18nService i18n;

    @GetMapping
    public String list(Model model) {
        User user = currentUserHelper.currentUser();
        model.addAttribute("user", user);
        model.addAttribute("environments", environmentService.packageViews(user));
        model.addAttribute("globals", environmentService.globalViews(user));
        model.addAttribute("activeEnvironmentId",
                environmentService.activePackageId(user).orElse(null));
        return "environments/list";
    }

    @PostMapping("/active")
    public String setActive(@RequestParam(required = false) Long environmentId,
                            RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        environmentService.setActiveEnvironment(user, environmentId);
        ra.addFlashAttribute("successMessage", i18n.t("flash.environmentActiveUpdated"));
        return "redirect:/environments";
    }

    @PostMapping("/new")
    public String create(@RequestParam String name,
                         @RequestParam(required = false) String description,
                         @RequestParam(defaultValue = "false") boolean makeActive,
                         RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        var environment = environmentService.createPackage(name, description, makeActive, user);
        ra.addFlashAttribute("successMessage",
                i18n.t("flash.environmentCreated", environment.getName()));
        return "redirect:/environments/" + environment.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        return environmentService.packageView(id, user).map(environment -> {
            model.addAttribute("user", user);
            model.addAttribute("environment", environment);
            return "environments/detail";
        }).orElseGet(() -> {
            ra.addFlashAttribute("errorMessage", i18n.t("flash.environmentNotFound"));
            return "redirect:/environments";
        });
    }

    @PostMapping("/globals")
    public String saveGlobals(@RequestParam(required = false) List<String> keys,
                              @RequestParam(required = false) List<String> values,
                              @RequestParam(required = false) List<String> descriptions,
                              @RequestParam(required = false) List<String> hidden,
                              RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        environmentService.saveGlobals(user, variableInputs(keys, values, descriptions, hidden));
        ra.addFlashAttribute("successMessage", i18n.t("flash.environmentGlobalsSaved"));
        return "redirect:/environments";
    }

    @PostMapping("/{id}/variables")
    public String saveVariables(@PathVariable Long id,
                                @RequestParam(required = false) List<String> keys,
                                @RequestParam(required = false) List<String> values,
                                @RequestParam(required = false) List<String> descriptions,
                                @RequestParam(required = false) List<String> hidden,
                                RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        boolean saved = environmentService
                .savePackageVariables(id, user, variableInputs(keys, values, descriptions, hidden))
                .isPresent();
        ra.addFlashAttribute(saved ? "successMessage" : "errorMessage",
                saved ? i18n.t("flash.environmentSaved") : i18n.t("flash.environmentNotFound"));
        return saved ? "redirect:/environments/" + id : "redirect:/environments";
    }

    @PostMapping("/duplicate")
    public String duplicate(@RequestParam Long sourceId,
                            @RequestParam String name,
                            RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        environmentService.duplicatePackage(sourceId, name, user).ifPresentOrElse(
                copied -> ra.addFlashAttribute("successMessage",
                        i18n.t("flash.environmentDuplicated", copied.getName())),
                () -> ra.addFlashAttribute("errorMessage", i18n.t("flash.environmentNotFound")));
        return "redirect:/environments";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        boolean deleted = environmentService.deletePackage(id, user);
        ra.addFlashAttribute(deleted ? "successMessage" : "errorMessage",
                deleted ? i18n.t("flash.environmentDeleted") : i18n.t("flash.environmentNotFound"));
        return "redirect:/environments";
    }

    @GetMapping("/globals/export")
    public ResponseEntity<byte[]> exportGlobals() throws IOException {
        User user = currentUserHelper.currentUser();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + environmentService.globalsFilename() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(environmentService.exportGlobals(user));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportPackage(@PathVariable Long id) throws IOException {
        User user = currentUserHelper.currentUser();
        var environment = environmentService.packageView(id, user);
        if (environment.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return environmentService.exportPackage(id, user)
                .map(data -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" +
                                        environmentService.filenameForPackage(environment.get()) + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(data))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/globals/import")
    public String importGlobals(@RequestParam("file") MultipartFile file,
                                @RequestParam(defaultValue = "merge") String strategy,
                                RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        if (file.isEmpty()) {
            ra.addFlashAttribute("errorMessage", i18n.t("flash.selectFile"));
            return "redirect:/environments";
        }
        try {
            var result = environmentService.importGlobals(file.getBytes(), user, strategy);
            ra.addFlashAttribute("successMessage",
                    i18n.t("flash.environmentImported", result.variables(), result.packages()));
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", i18n.t("flash.importFailed", e.getMessage()));
        }
        return "redirect:/environments";
    }

    @PostMapping("/import")
    public String importPackage(@RequestParam("file") MultipartFile file,
                                @RequestParam(defaultValue = "copy") String strategy,
                                RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        if (file.isEmpty()) {
            ra.addFlashAttribute("errorMessage", i18n.t("flash.selectFile"));
            return "redirect:/environments";
        }
        try {
            var result = environmentService.importPackage(file.getBytes(), user, strategy);
            ra.addFlashAttribute("successMessage",
                    i18n.t("flash.environmentImported", result.variables(), result.packages()));
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", i18n.t("flash.importFailed", e.getMessage()));
        }
        return "redirect:/environments";
    }

    private List<EnvironmentService.VariableInput> variableInputs(List<String> keys,
                                                                  List<String> values,
                                                                  List<String> descriptions,
                                                                  List<String> hidden) {
        int size = maxSize(keys, values, descriptions, hidden);
        List<EnvironmentService.VariableInput> inputs = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            inputs.add(new EnvironmentService.VariableInput(
                    valueAt(keys, i),
                    valueAt(values, i),
                    valueAt(descriptions, i),
                    Boolean.parseBoolean(valueAt(hidden, i))));
        }
        return inputs;
    }

    private int maxSize(List<?>... lists) {
        int size = 0;
        for (List<?> list : lists) {
            if (list != null) {
                size = Math.max(size, list.size());
            }
        }
        return size;
    }

    private String valueAt(List<String> values, int index) {
        return values != null && index < values.size() ? values.get(index) : "";
    }
}
