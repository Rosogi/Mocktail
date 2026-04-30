package com.rosogisoft.web;

import com.rosogisoft.domain.User;
import com.rosogisoft.repository.MockDefinitionRepository;
import com.rosogisoft.service.MockImportExportService;
import com.rosogisoft.service.MockCollectionService;
import com.rosogisoft.service.MockService;
import com.rosogisoft.service.SharedCollectionService;
import com.rosogisoft.web.dto.ImportMode;
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

@Controller
@RequestMapping("/collections")
@RequiredArgsConstructor
public class MockCollectionController {

    private final MockCollectionService collectionService;
    private final MockImportExportService exportService;
    private final CurrentUserHelper        currentUserHelper;
    private final MockDefinitionRepository mockRepository;
    private final MockService mockService;
    private final SharedCollectionService sharedCollectionService;

    // ── List ─────────────────────────────────────────────────────────
    @GetMapping
    public String list(Model model) {
        User user = currentUserHelper.currentUser();
        var collections = collectionService.findAllForUser(user);
        model.addAttribute("user",        user);
        model.addAttribute("collections", collections);
        // Attach mock counts
        var counts = new java.util.HashMap<Long, Integer>();
        collections.forEach(c -> counts.put(c.getId(), collectionService.countMocks(c.getId())));
        model.addAttribute("mockCounts", counts);
        var subscriptions = sharedCollectionService.subscriptionsByLocal(user);
        var updates = new java.util.HashMap<Long, Boolean>();
        var sourceAvailable = new java.util.HashMap<Long, Boolean>();
        subscriptions.forEach((id, subscription) -> {
            updates.put(id, sharedCollectionService.isUpdateAvailable(subscription));
            sourceAvailable.put(id, sharedCollectionService.isSourceAvailable(subscription));
        });
        model.addAttribute("subscriptions", subscriptions);
        model.addAttribute("updateAvailable", updates);
        model.addAttribute("sourceAvailable", sourceAvailable);
        return "collections/list";
    }

    // ── Create ───────────────────────────────────────────────────────
    @PostMapping("/new")
    public String create(@RequestParam String name,
                         @RequestParam(required = false) String description,
                         RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        collectionService.create(name, description, user);
        ra.addFlashAttribute("successMessage", "Collection \"" + name + "\" created.");
        return "redirect:/collections";
    }

    // ── Edit (inline form on list page) ──────────────────────────────
    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @RequestParam String name,
                       @RequestParam(required = false) String description,
                       RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        try {
            boolean ok = collectionService.update(id, name, description, user).isPresent();
            ra.addFlashAttribute(ok ? "successMessage" : "errorMessage",
                    ok ? "Collection updated." : "Collection not found.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/collections";
    }

    // ── Delete ───────────────────────────────────────────────────────
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        try {
            boolean ok = collectionService.delete(id, user);
            ra.addFlashAttribute(ok ? "successMessage" : "errorMessage",
                    ok ? "Collection deleted. Mocks were kept (uncollected)."
                            : "Collection not found.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/collections";
    }

    // ── Enable all ───────────────────────────────────────────────────
    @PostMapping("/{id}/enable-all")
    public String enableAll(@PathVariable Long id, RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        try {
            collectionService.enableAll(id, user);
            ra.addFlashAttribute("successMessage", "All mocks in collection enabled.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/collections";
    }

    // ── Disable all ──────────────────────────────────────────────────
    @PostMapping("/{id}/disable-all")
    public String disableAll(@PathVariable Long id, RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        try {
            collectionService.disableAll(id, user);
            ra.addFlashAttribute("successMessage", "All mocks in collection disabled.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/collections";
    }

    @PostMapping("/{id}/share")
    public String share(@PathVariable Long id, RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        try {
            boolean ok = sharedCollectionService.shareCollection(id, user);
            ra.addFlashAttribute(ok ? "successMessage" : "errorMessage",
                    ok ? "Collection shared." : "Collection not found.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/collections";
    }

    @PostMapping("/{id}/unshare")
    public String unshare(@PathVariable Long id, RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        try {
            boolean ok = sharedCollectionService.unshareCollection(id, user);
            ra.addFlashAttribute(ok ? "successMessage" : "errorMessage",
                    ok ? "Collection is no longer shared." : "Collection not found.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/collections";
    }

    // ── Export single collection ─────────────────────────────────────
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportCollection(@PathVariable Long id) throws IOException {
        User user = currentUserHelper.currentUser();
        return collectionService.findByIdForUser(id, user).map(collection -> {
            try {
                byte[] data = exportService.exportCollection(collection, user);
                String filename = slugify(collection.getName()) + "-mocks.json";
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(data);
            } catch (IOException e) {
                throw new RuntimeException("Export failed", e);
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/import")
    public String importCollection(@RequestParam("file") MultipartFile file,
                                   RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        if (file.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Please select a file to import.");
            return "redirect:/collections";
        }
        try {
            MockImportExportService.ImportResult result =
                    exportService.importFromJson(
                            file.getBytes(), user,
                            ImportMode.COLLECTIONS_ONLY);
            ra.addFlashAttribute("successMessage",
                    "Imported %d mocks in %d collections."
                            .formatted(result.mocks(), result.collections()));
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Import failed: " + e.getMessage());
        }
        return "redirect:/collections";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        return collectionService.findByIdForUser(id, user).map(collection -> {
            model.addAttribute("user",       user);
            model.addAttribute("collection", collection);
            model.addAttribute("mocks",      mockRepository.findByCollectionId(id));
            model.addAttribute("allMocks",   mockService.findAllForUser(user));
            var subscriptions = sharedCollectionService.subscriptionsByLocal(user);
            var subscription = subscriptions.get(id);
            model.addAttribute("subscription", subscription);
            model.addAttribute("hasUpdate", subscription != null &&
                    sharedCollectionService.isUpdateAvailable(subscription));
            model.addAttribute("sourceAvailable", subscription != null &&
                    sharedCollectionService.isSourceAvailable(subscription));
            return "collections/detail";
        }).orElseGet(() -> {
            ra.addFlashAttribute("errorMessage", "Collection not found.");
            return "redirect:/collections";
        });
    }

    @PostMapping("/{id}/add-mock")
    public String addMock(@PathVariable Long id,
                          @RequestParam Long mockId,
                          RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        try {
            collectionService.addMock(id, mockId, user);
            ra.addFlashAttribute("successMessage", "Mock added to collection.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/collections/" + id;
    }

    @PostMapping("/{id}/remove-mock")
    public String removeMock(@PathVariable Long id,
                             @RequestParam Long mockId,
                             RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        try {
            collectionService.removeMock(id, mockId, user);
            ra.addFlashAttribute("successMessage", "Mock removed from collection.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/collections/" + id;
    }

    private String slugify(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
