package com.rosogisoft.web;

import com.rosogisoft.config.ApplicationCapabilities;
import com.rosogisoft.domain.User;
import com.rosogisoft.repository.MockDefinitionRepository;
import com.rosogisoft.service.I18nService;
import com.rosogisoft.service.MockFunctionService;
import com.rosogisoft.service.SharedCollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/shared")
@RequiredArgsConstructor
public class SharedController {

    private final CurrentUserHelper currentUserHelper;
    private final SharedCollectionService sharedCollectionService;
    private final MockFunctionService functionService;
    private final MockDefinitionRepository mockRepository;
    private final I18nService i18n;
    private final ApplicationCapabilities capabilities;

    @GetMapping
    public String list() {
        ensureSharedEnabled();
        return "redirect:/shared/collections";
    }

    @GetMapping("/collections")
    public String collections(Model model) {
        ensureSharedEnabled();
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
        return "shared/collections";
    }

    @GetMapping("/functions")
    public String functions(Model model) {
        ensureSharedEnabled();
        User user = currentUserHelper.currentUser();
        model.addAttribute("user", user);
        model.addAttribute("sharedFunctions", functionService.sharedFunctions(user));
        return "shared/functions";
    }

    @GetMapping({"/{id}", "/collections/{id}"})
    public String detail(@PathVariable Long id, Model model, RedirectAttributes ra) {
        ensureSharedEnabled();
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
            ra.addFlashAttribute("errorMessage", i18n.t("flash.collectionNotFound"));
            return "redirect:/shared/collections";
        }
    }

    @PostMapping({"/{id}/subscribe", "/collections/{id}/subscribe"})
    public String subscribe(@PathVariable Long id, RedirectAttributes ra) {
        ensureSharedEnabled();
        User user = currentUserHelper.currentUser();
        try {
            var local = sharedCollectionService.subscribe(id, user);
            ra.addFlashAttribute("successMessage", i18n.t("flash.subscribed"));
            return "redirect:/collections/" + local.getId();
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", sharedErrorMessage(e));
            return "redirect:/shared/collections";
        }
    }

    @PostMapping({"/{id}/copy", "/collections/{id}/copy"})
    public String copy(@PathVariable Long id, RedirectAttributes ra) {
        ensureSharedEnabled();
        User user = currentUserHelper.currentUser();
        try {
            var local = sharedCollectionService.copySharedCollection(id, user);
            ra.addFlashAttribute("successMessage", i18n.t("flash.collectionCopied"));
            return "redirect:/collections/" + local.getId();
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", sharedErrorMessage(e));
            return "redirect:/shared/collections";
        }
    }

    @PostMapping({"/{id}/update", "/collections/{id}/update"})
    public String updateFromSource(@PathVariable Long id, RedirectAttributes ra) {
        ensureSharedEnabled();
        User user = currentUserHelper.currentUser();
        try {
            var local = sharedCollectionService.updateSubscriptionBySource(id, user);
            ra.addFlashAttribute("successMessage", i18n.t("flash.subscriptionUpdated"));
            return "redirect:/collections/" + local.getId();
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", sharedErrorMessage(e));
            return "redirect:/shared/collections";
        }
    }

    @PostMapping("/subscriptions/{localId}/update")
    public String updateSubscription(@PathVariable Long localId, RedirectAttributes ra) {
        ensureSharedEnabled();
        User user = currentUserHelper.currentUser();
        try {
            var local = sharedCollectionService.updateSubscriptionByLocal(localId, user);
            ra.addFlashAttribute("successMessage", i18n.t("flash.subscriptionUpdated"));
            return "redirect:/collections/" + local.getId();
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", sharedErrorMessage(e));
            return "redirect:/collections/" + localId;
        }
    }

    @PostMapping("/subscriptions/{localId}/copy")
    public String copySubscription(@PathVariable Long localId, RedirectAttributes ra) {
        ensureSharedEnabled();
        User user = currentUserHelper.currentUser();
        try {
            var local = sharedCollectionService.copySubscribedCollection(localId, user);
            ra.addFlashAttribute("successMessage", i18n.t("flash.editableCopyCreated"));
            return "redirect:/collections/" + local.getId();
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", sharedErrorMessage(e));
            return "redirect:/collections/" + localId;
        }
    }

    @PostMapping("/subscriptions/{localId}/unsubscribe")
    public String unsubscribe(@PathVariable Long localId, RedirectAttributes ra) {
        ensureSharedEnabled();
        User user = currentUserHelper.currentUser();
        boolean ok = sharedCollectionService.unsubscribe(localId, user);
        ra.addFlashAttribute(ok ? "successMessage" : "errorMessage",
                ok ? i18n.t("flash.subscriptionRemoved") : i18n.t("flash.subscriptionNotFound"));
        return "redirect:/collections";
    }

    @PostMapping("/functions/{id}/subscribe")
    public String subscribeFunction(@PathVariable Long id, RedirectAttributes ra) {
        ensureSharedEnabled();
        User user = currentUserHelper.currentUser();
        try {
            functionService.subscribe(id, user);
            ra.addFlashAttribute("successMessage", i18n.t("flash.functionSubscribed"));
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", sharedErrorMessage(e));
        }
        return "redirect:/shared/functions";
    }

    @PostMapping("/functions/{id}/copy")
    public String copyFunction(@PathVariable Long id, RedirectAttributes ra) {
        ensureSharedEnabled();
        User user = currentUserHelper.currentUser();
        try {
            functionService.copyShared(id, user);
            ra.addFlashAttribute("successMessage", i18n.t("flash.functionCopied"));
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", sharedErrorMessage(e));
        }
        return "redirect:/shared/functions";
    }

    @PostMapping("/function-subscriptions/{localId}/update")
    public String updateFunctionSubscription(@PathVariable Long localId, RedirectAttributes ra) {
        ensureSharedEnabled();
        User user = currentUserHelper.currentUser();
        try {
            functionService.updateSubscription(localId, user);
            ra.addFlashAttribute("successMessage", i18n.t("flash.functionSubscriptionUpdated"));
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", sharedErrorMessage(e));
        }
        return "redirect:/shared/functions";
    }

    @PostMapping("/function-subscriptions/{localId}/unsubscribe")
    public String unsubscribeFunction(@PathVariable Long localId, RedirectAttributes ra) {
        ensureSharedEnabled();
        User user = currentUserHelper.currentUser();
        boolean ok = functionService.unsubscribe(localId, user);
        ra.addFlashAttribute(ok ? "successMessage" : "errorMessage",
                ok ? i18n.t("flash.functionSubscriptionRemoved") : i18n.t("flash.functionNotFound"));
        return "redirect:/shared/functions";
    }

    private void ensureSharedEnabled() {
        if (!capabilities.isShared()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private String sharedErrorMessage(RuntimeException e) {
        String message = e.getMessage();
        if ("Shared collection not found.".equals(message)) {
            return i18n.t("flash.collectionNotFound");
        }
        if ("Subscription not found.".equals(message)) {
            return i18n.t("flash.subscriptionNotFound");
        }
        if ("Source collection is no longer shared.".equals(message)) {
            return i18n.t("flash.sourceNoLongerShared");
        }
        if ("Subscription local copy is invalid.".equals(message)) {
            return i18n.t("flash.subscriptionInvalid");
        }
        if ("Subscribed collections are read-only.".equals(message)) {
            return i18n.t("flash.readOnlyCollection");
        }
        if ("Shared function not found.".equals(message)) {
            return i18n.t("flash.functionNotFound");
        }
        if ("Function subscription not found.".equals(message)) {
            return i18n.t("flash.functionNotFound");
        }
        if ("Source function is no longer shared.".equals(message)) {
            return i18n.t("flash.sourceNoLongerShared");
        }
        if ("Function subscription is invalid.".equals(message)) {
            return i18n.t("flash.subscriptionInvalid");
        }
        return message;
    }
}
