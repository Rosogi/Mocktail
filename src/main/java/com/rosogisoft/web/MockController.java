package com.rosogisoft.web;

import com.rosogisoft.domain.MockDefinition;
import com.rosogisoft.domain.User;
import com.rosogisoft.service.I18nService;
import com.rosogisoft.service.EnvironmentService;
import com.rosogisoft.service.MockCollectionService;
import com.rosogisoft.service.MockFunctionService;
import com.rosogisoft.service.MockImportExportService;
import com.rosogisoft.service.MockService;
import com.rosogisoft.service.MockTemplateReferenceService;
import com.rosogisoft.web.dto.ImportMode;
import com.rosogisoft.web.dto.MockDefinitionForm;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/mocks")
@RequiredArgsConstructor
public class MockController {

    private final MockService mockService;
    private final CurrentUserHelper currentUserHelper;
    private final MockImportExportService importExportService;
    private final MockCollectionService collectionService;
    private final EnvironmentService environmentService;
    private final MockFunctionService functionService;
    private final MockTemplateReferenceService templateReferenceService;
    private final I18nService i18n;

    // ── List ─────────────────────────────────────────────────────────
    @GetMapping
    public String list (Model model) {
        User user = currentUserHelper.currentUser();
        var mocks = mockService.findAllForUser(user);
        model.addAttribute("user", user);
        model.addAttribute("mocks", mocks);
        model.addAttribute("mockTemplateWarnings", templateReferenceService.analyzeAll(mocks, user));
        model.addAttribute("collections", collectionService.findAllForUser(user));
        return "mocks/list";
    }

    // ── Create form ──────────────────────────────────────────────────
    @GetMapping("/new")
    public String createForm (Model model) {
        User user = currentUserHelper.currentUser();
        model.addAttribute("user", user);
        model.addAttribute("form", new MockDefinitionForm());
        model.addAttribute("httpMethods", HTTP_METHODS);
        model.addAttribute("contentTypes", CONTENT_TYPES);
        model.addAttribute("collections",  collectionService.findEditableForUser(user));
        model.addAttribute("editing", false);
        addTemplateModel(model, user, new MockDefinitionForm());
        return "mocks/form";
    }

    // ── Create submit ────────────────────────────────────────────────
    @PostMapping("/new")
    public String create (@ModelAttribute("form") MockDefinitionForm form,
                          RedirectAttributes redirectAttributes) {
        User user = currentUserHelper.currentUser();
        try {
            MockDefinition created = mockService.create(form, user);
            redirectAttributes.addFlashAttribute("successMessage",
                    i18n.t("flash.mockCreated", created.getName()));
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", i18n.t("flash.readOnlyCollection"));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/mocks";
    }

    // ── Edit form ────────────────────────────────────────────────────
    @GetMapping("/{id}/edit")
    public String editForm (@PathVariable Long id, Model model,
                            RedirectAttributes redirectAttributes) {
        User user = currentUserHelper.currentUser();
        return mockService.findByIdForUser(id, user).map(mock -> {
            if (mock.getCollection() != null && mock.getCollection().isReadOnly()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        i18n.t("flash.readOnlyCollection"));
                return "redirect:/mocks";
            }
            model.addAttribute("user", user);
            model.addAttribute("form", toForm(mock));
            model.addAttribute("mockId", id);
            model.addAttribute("httpMethods", HTTP_METHODS);
            model.addAttribute("contentTypes", CONTENT_TYPES);
            model.addAttribute("collections",  collectionService.findEditableForUser(user));
            model.addAttribute("editing", true);
            addTemplateModel(model, user, toForm(mock));
            return "mocks/form";
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("errorMessage", i18n.t("flash.mockNotFound"));
            return "redirect:/mocks";
        });
    }

    // ── Edit submit ──────────────────────────────────────────────────
    @PostMapping("/{id}/edit")
    public String update (@PathVariable Long id,
                          @ModelAttribute("form") MockDefinitionForm form,
                          RedirectAttributes redirectAttributes) {
        User user = currentUserHelper.currentUser();
        try {
            boolean updated = mockService.update(id, form, user).isPresent();
            if (updated) {
                redirectAttributes.addFlashAttribute("successMessage", i18n.t("flash.mockUpdated"));
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", i18n.t("flash.mockNotFound"));
            }
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", i18n.t("flash.readOnlyCollection"));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/mocks";
    }

    // ── Copy ─────────────────────────────────────────────────────────
    @PostMapping("/{id}/copy")
    public String copy (@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = currentUserHelper.currentUser();
        try {
            mockService.copy(id, user).ifPresentOrElse(
                    copied -> redirectAttributes.addFlashAttribute(
                            "successMessage",
                            i18n.t("flash.mockCopied", copied.getName())),
                    () -> redirectAttributes.addFlashAttribute(
                            "errorMessage",
                            i18n.t("flash.mockNotFound")));
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", i18n.t("flash.readOnlyCollection"));
        }
        return "redirect:/mocks";
    }

    // ── Toggle active ────────────────────────────────────────────────
    @PostMapping("/{id}/toggle")
    public String toggle (@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = currentUserHelper.currentUser();
        try {
            mockService.toggleActive(id, user);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", i18n.t("flash.readOnlyCollection"));
        }
        return "redirect:/mocks";
    }

    // ── Delete ───────────────────────────────────────────────────────
    @PostMapping("/{id}/delete")
    public String delete (@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = currentUserHelper.currentUser();
        try {
            boolean deleted = mockService.delete(id, user);
            redirectAttributes.addFlashAttribute(
                    deleted ? "successMessage" : "errorMessage",
                    deleted ? i18n.t("flash.mockDeleted") : i18n.t("flash.mockNotFound"));
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", i18n.t("flash.readOnlyCollection"));
        }
        return "redirect:/mocks";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportAllMocks() throws IOException {
        User user = currentUserHelper.currentUser();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"mocks-export.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(importExportService.exportAll(user));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportSingleMock (@PathVariable Long id) throws IOException {
        User user = currentUserHelper.currentUser();
        return mockService.findByIdForUser(id, user).map(mock -> {
            try {
                byte[] data = importExportService.exportSingleMock(mock, user);
                String filename = mock.getName()
                        .toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-mock.json";
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + filename + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(data);
            } catch (IOException e) {
                throw new RuntimeException("Export failed", e);
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/import")
    public String importMocks(@RequestParam("file") MultipartFile file,
                              RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        if (file.isEmpty()) {
            ra.addFlashAttribute("errorMessage", i18n.t("flash.selectFile"));
            return "redirect:/mocks";
        }
        try {
            MockImportExportService.ImportResult result =
                    importExportService.importFromJson(
                            file.getBytes(),
                            user,
                            ImportMode.MOCKS_ONLY);
            ra.addFlashAttribute("successMessage",
                    i18n.t("flash.imported", result.mocks(), result.collections()));
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", importErrorMessage(e));
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", i18n.t("flash.importFailed", e.getMessage()));
        }
        return "redirect:/mocks";
    }

    // ── Helpers ──────────────────────────────────────────────────────
    private String importErrorMessage(IllegalArgumentException e) {
        String message = e.getMessage();
        if ("This file contains collections. Use 'Import collection' on the Collections page.".equals(message)) {
            return i18n.t("flash.importContainsCollections");
        }
        if ("This file contains standalone mocks. Use 'Import' on the Mocks page.".equals(message)) {
            return i18n.t("flash.importContainsStandaloneMocks");
        }
        if (message != null && message.contains("read-only subscription")) {
            return i18n.t("flash.importReadOnlySubscription");
        }
        return i18n.t("flash.importFailed", message);
    }

    private void addTemplateModel(Model model, User user, MockDefinitionForm form) {
        var suggestions = new java.util.ArrayList<>(environmentService.templateSuggestions(user));
        suggestions.addAll(functionService.templateSuggestions(user));
        model.addAttribute("templateSuggestions", suggestions);
        model.addAttribute("templateWarnings", templateReferenceService.analyze(form, user));
    }

    private MockDefinitionForm toForm (MockDefinition mock) {
        MockDefinitionForm form = new MockDefinitionForm();
        form.setName(mock.getName());
        form.setHttpMethod(mock.getHttpMethod());
        form.setPathPattern(mock.getPathPattern());
        form.setRequestBodyContains(mock.getRequestBodyContains());
        form.setRequestMatchMode(mock.getRequestMatchMode() != null ? mock.getRequestMatchMode() : "basic");
        form.setRequestMatchGroups(mock.getRequestMatchGroups());
        form.setResponseStatus(mock.getResponseStatus());
        form.setResponseBody(mock.getResponseBody());
        form.setResponseContentType(mock.getResponseContentType());
        form.setPriority(mock.getPriority());
        form.setActive(mock.isActive());
        form.setCollectionId(mock.getCollection() != null ? mock.getCollection().getId() : null);
        // Serialize headers map back to raw text
        if (mock.getResponseHeaders() != null) {
            String raw = mock.getResponseHeaders().entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining("\n"));
            form.setResponseHeadersRaw(raw);
        }
        return form;
    }

    private static final Map<String, String> HTTP_METHODS = new LinkedHashMap<>() {{
        put("*", "* (Any)");
        put("GET", "GET");
        put("POST", "POST");
        put("PUT", "PUT");
        put("PATCH", "PATCH");
        put("DELETE", "DELETE");
        put("HEAD", "HEAD");
        put("OPTIONS", "OPTIONS");
    }};

    private static final Map<String, String> CONTENT_TYPES = new LinkedHashMap<>() {{
        put("application/json", "application/json");
        put("application/xml", "application/xml");
        put("application/soap+xml", "application/soap+xml (SOAP)");
        put("text/xml", "text/xml (SOAP)");
        put("text/plain", "text/plain");
        put("text/html", "text/html");
        put("application/x-www-form-urlencoded", "application/x-www-form-urlencoded");
    }};
}
