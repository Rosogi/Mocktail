package com.rosogisoft.web;

import com.rosogisoft.domain.MockDefinition;
import com.rosogisoft.domain.User;
import com.rosogisoft.service.MockCollectionService;
import com.rosogisoft.service.MockImportExportService;
import com.rosogisoft.service.MockService;
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

    // ── List ─────────────────────────────────────────────────────────
    @GetMapping
    public String list (Model model) {
        User user = currentUserHelper.currentUser();
        model.addAttribute("user", user);
        model.addAttribute("mocks", mockService.findAllForUser(user));
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
                    "Mock \"" + created.getName() + "\" created.");
        } catch (IllegalStateException e) {
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
                        "Subscribed collections are read-only.");
                return "redirect:/mocks";
            }
            model.addAttribute("user", user);
            model.addAttribute("form", toForm(mock));
            model.addAttribute("mockId", id);
            model.addAttribute("httpMethods", HTTP_METHODS);
            model.addAttribute("contentTypes", CONTENT_TYPES);
            model.addAttribute("editing", true);
            return "mocks/form";
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("errorMessage", "Mock not found.");
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
                redirectAttributes.addFlashAttribute("successMessage", "Mock updated.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Mock not found.");
            }
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
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
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
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
                    deleted ? "Mock deleted." : "Mock not found.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/mocks";
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
            ra.addFlashAttribute("errorMessage", "Please select a file to import.");
            return "redirect:/mocks";
        }
        try {
            MockImportExportService.ImportResult result =
                    importExportService.importFromJson(
                            file.getBytes(),
                            user,
                            ImportMode.MOCKS_ONLY);
            ra.addFlashAttribute("successMessage",
                    "Imported %d mocks in %d collections."
                            .formatted(result.mocks(), result.collections()));
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Import failed: " + e.getMessage());
        }
        return "redirect:/mocks";
    }

    // ── Helpers ──────────────────────────────────────────────────────
    private MockDefinitionForm toForm (MockDefinition mock) {
        MockDefinitionForm form = new MockDefinitionForm();
        form.setName(mock.getName());
        form.setHttpMethod(mock.getHttpMethod());
        form.setPathPattern(mock.getPathPattern());
        form.setRequestBodyContains(mock.getRequestBodyContains());
        form.setResponseStatus(mock.getResponseStatus());
        form.setResponseBody(mock.getResponseBody());
        form.setResponseContentType(mock.getResponseContentType());
        form.setPriority(mock.getPriority());
        form.setActive(mock.isActive());
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
        put("text/xml", "text/xml (SOAP)");
        put("text/plain", "text/plain");
        put("text/html", "text/html");
        put("application/x-www-form-urlencoded", "application/x-www-form-urlencoded");
    }};
}
