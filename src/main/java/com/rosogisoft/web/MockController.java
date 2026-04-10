package com.rosogisoft.web;

import com.rosogisoft.domain.MockDefinition;
import com.rosogisoft.domain.User;
import com.rosogisoft.service.MockService;
import com.rosogisoft.web.dto.MockDefinitionForm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/mocks")
@RequiredArgsConstructor
public class MockController {

    private final MockService mockService;
    private final CurrentUserHelper currentUserHelper;

    // ── List ─────────────────────────────────────────────────────────
    @GetMapping
    public String list (Model model) {
        User user = currentUserHelper.currentUser();
        model.addAttribute("user", user);
        model.addAttribute("mocks", mockService.findAllForUser(user));
        return "mocks/list";
    }

    // ── Create form ──────────────────────────────────────────────────
    @GetMapping("/new")
    public String createForm (Model model) {
        model.addAttribute("user", currentUserHelper.currentUser());
        model.addAttribute("form", new MockDefinitionForm());
        model.addAttribute("httpMethods", HTTP_METHODS);
        model.addAttribute("contentTypes", CONTENT_TYPES);
        model.addAttribute("editing", false);
        return "mocks/form";
    }

    // ── Create submit ────────────────────────────────────────────────
    @PostMapping("/new")
    public String create (@ModelAttribute("form") MockDefinitionForm form,
                          RedirectAttributes redirectAttributes) {
        User user = currentUserHelper.currentUser();
        MockDefinition created = mockService.create(form, user);
        redirectAttributes.addFlashAttribute("successMessage",
                "Mock \"" + created.getName() + "\" created.");
        return "redirect:/mocks";
    }

    // ── Edit form ────────────────────────────────────────────────────
    @GetMapping("/{id}/edit")
    public String editForm (@PathVariable Long id, Model model,
                            RedirectAttributes redirectAttributes) {
        User user = currentUserHelper.currentUser();
        return mockService.findByIdForUser(id, user).map(mock -> {
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
        boolean updated = mockService.update(id, form, user).isPresent();
        if (updated) {
            redirectAttributes.addFlashAttribute("successMessage", "Mock updated.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Mock not found.");
        }
        return "redirect:/mocks";
    }

    // ── Toggle active ────────────────────────────────────────────────
    @PostMapping("/{id}/toggle")
    public String toggle (@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = currentUserHelper.currentUser();
        mockService.toggleActive(id, user);
        return "redirect:/mocks";
    }

    // ── Delete ───────────────────────────────────────────────────────
    @PostMapping("/{id}/delete")
    public String delete (@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = currentUserHelper.currentUser();
        boolean deleted = mockService.delete(id, user);
        redirectAttributes.addFlashAttribute(
                deleted ? "successMessage" : "errorMessage",
                deleted ? "Mock deleted." : "Mock not found.");
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
