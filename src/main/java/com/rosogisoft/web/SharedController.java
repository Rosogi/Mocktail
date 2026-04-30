package com.rosogisoft.web;

import com.rosogisoft.domain.User;
import com.rosogisoft.repository.MockDefinitionRepository;
import com.rosogisoft.service.SharedCollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/shared")
@RequiredArgsConstructor
public class SharedController {

    private final CurrentUserHelper currentUserHelper;
    private final SharedCollectionService sharedCollectionService;
    private final MockDefinitionRepository mockRepository;

    @GetMapping
    public String list(Model model) {
        User user = currentUserHelper.currentUser();
        var sharedCollections = sharedCollectionService.findSharedForUser(user);
        var mockCounts = new java.util.HashMap<Long, Long>();
        sharedCollections.forEach(collection ->
                mockCounts.put(collection.getId(), sharedCollectionService.countMocks(collection)));

        var subscriptions = sharedCollectionService.subscriptionsBySource(user);
        var updates = new java.util.HashMap<Long, Boolean>();
        subscriptions.forEach((sourceId, subscription) ->
                updates.put(sourceId, sharedCollectionService.isUpdateAvailable(subscription)));

        model.addAttribute("user", user);
        model.addAttribute("sharedCollections", sharedCollections);
        model.addAttribute("mockCounts", mockCounts);
        model.addAttribute("subscriptions", subscriptions);
        model.addAttribute("updateAvailable", updates);
        return "shared/index";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        try {
            var source = sharedCollectionService.findSharedSource(id, user);
            var subscriptions = sharedCollectionService.subscriptionsBySource(user);
            var subscription = subscriptions.get(id);
            model.addAttribute("user", user);
            model.addAttribute("collection", source);
            model.addAttribute("mocks", mockRepository.findByCollectionId(source.getId()));
            model.addAttribute("subscription", subscription);
            model.addAttribute("hasUpdate", subscription != null &&
                    sharedCollectionService.isUpdateAvailable(subscription));
            return "shared/detail";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/shared";
        }
    }

    @PostMapping("/{id}/subscribe")
    public String subscribe(@PathVariable Long id, RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        try {
            var local = sharedCollectionService.subscribe(id, user);
            ra.addFlashAttribute("successMessage", "Subscribed to collection.");
            return "redirect:/collections/" + local.getId();
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/shared";
        }
    }

    @PostMapping("/{id}/copy")
    public String copy(@PathVariable Long id, RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        try {
            var local = sharedCollectionService.copySharedCollection(id, user);
            ra.addFlashAttribute("successMessage", "Collection copied.");
            return "redirect:/collections/" + local.getId();
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/shared";
        }
    }

    @PostMapping("/{id}/update")
    public String updateFromSource(@PathVariable Long id, RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        try {
            var local = sharedCollectionService.updateSubscriptionBySource(id, user);
            ra.addFlashAttribute("successMessage", "Subscription updated.");
            return "redirect:/collections/" + local.getId();
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/shared";
        }
    }

    @PostMapping("/subscriptions/{localId}/update")
    public String updateSubscription(@PathVariable Long localId, RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        try {
            var local = sharedCollectionService.updateSubscriptionByLocal(localId, user);
            ra.addFlashAttribute("successMessage", "Subscription updated.");
            return "redirect:/collections/" + local.getId();
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/collections/" + localId;
        }
    }

    @PostMapping("/subscriptions/{localId}/copy")
    public String copySubscription(@PathVariable Long localId, RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        try {
            var local = sharedCollectionService.copySubscribedCollection(localId, user);
            ra.addFlashAttribute("successMessage", "Editable copy created.");
            return "redirect:/collections/" + local.getId();
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/collections/" + localId;
        }
    }

    @PostMapping("/subscriptions/{localId}/unsubscribe")
    public String unsubscribe(@PathVariable Long localId, RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        boolean ok = sharedCollectionService.unsubscribe(localId, user);
        ra.addFlashAttribute(ok ? "successMessage" : "errorMessage",
                ok ? "Subscription removed." : "Subscription not found.");
        return "redirect:/collections";
    }
}
