package com.rosogisoft.web;

import com.rosogisoft.service.I18nService;
import com.rosogisoft.service.KnownRemoteHostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/settings/known-hosts")
@RequiredArgsConstructor
public class KnownRemoteHostSettingsController {

    private final CurrentUserHelper currentUserHelper;
    private final KnownRemoteHostService hostService;
    private final I18nService i18n;

    @PostMapping
    public String save(@RequestParam(required = false) List<String> addresses,
                       @RequestParam(required = false) List<String> displayNames,
                       @RequestParam(required = false) List<String> descriptions,
                       RedirectAttributes ra) {
        var user = currentUserHelper.currentUser();
        try {
            int saved = hostService.saveAll(user, hostInputs(addresses, displayNames, descriptions));
            ra.addFlashAttribute("successMessage", i18n.t("flash.knownHostsSaved", saved));
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", i18n.t("flash.knownHostsSaveFailed", e.getMessage()));
        }
        return "redirect:/settings";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportHosts() throws IOException {
        var user = currentUserHelper.currentUser();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + hostService.exportFilename() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(hostService.exportAll(user));
    }

    @PostMapping("/import")
    public String importHosts(@RequestParam("file") MultipartFile file,
                              @RequestParam(defaultValue = "merge") String strategy,
                              RedirectAttributes ra) {
        var user = currentUserHelper.currentUser();
        if (file.isEmpty()) {
            ra.addFlashAttribute("errorMessage", i18n.t("flash.selectFile"));
            return "redirect:/settings";
        }
        try {
            var result = hostService.importFromJson(file.getBytes(), user, strategy);
            ra.addFlashAttribute("successMessage",
                    i18n.t("flash.knownHostsImported", result.processed(), result.added(), result.updated()));
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", i18n.t("flash.importFailed", e.getMessage()));
        }
        return "redirect:/settings";
    }

    private List<KnownRemoteHostService.HostInput> hostInputs(List<String> addresses,
                                                              List<String> displayNames,
                                                              List<String> descriptions) {
        int size = maxSize(addresses, displayNames, descriptions);
        List<KnownRemoteHostService.HostInput> inputs = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            inputs.add(new KnownRemoteHostService.HostInput(
                    valueAt(addresses, index),
                    valueAt(displayNames, index),
                    valueAt(descriptions, index)));
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

