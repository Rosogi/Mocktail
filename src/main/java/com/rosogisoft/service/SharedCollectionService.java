package com.rosogisoft.service;

import com.rosogisoft.domain.CollectionSubscription;
import com.rosogisoft.domain.MockCollection;
import com.rosogisoft.domain.MockDefinition;
import com.rosogisoft.domain.User;
import com.rosogisoft.repository.CollectionSubscriptionRepository;
import com.rosogisoft.repository.MockCollectionRepository;
import com.rosogisoft.repository.MockDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SharedCollectionService {

    private static final int MAX_COLLECTION_NAME_LENGTH = 255;

    private final MockCollectionRepository collectionRepository;
    private final MockDefinitionRepository mockRepository;
    private final CollectionSubscriptionRepository subscriptionRepository;
    private final MockFunctionReferenceService functionReferenceService;

    public List<MockCollection> findSharedForUser(User user) {
        return collectionRepository.findSharedExcludingOwner(user.getId());
    }

    public long countMocks(MockCollection collection) {
        return mockRepository.countByCollectionId(collection.getId());
    }

    public Map<Long, CollectionSubscription> subscriptionsBySource(User user) {
        Map<Long, CollectionSubscription> result = new HashMap<>();
        subscriptionRepository.findBySubscriberId(user.getId()).forEach(subscription -> {
            if (subscription.getSourceCollection() != null) {
                result.put(subscription.getSourceCollection().getId(), subscription);
            }
        });
        return result;
    }

    public Map<Long, CollectionSubscription> subscriptionsByLocal(User user) {
        Map<Long, CollectionSubscription> result = new HashMap<>();
        subscriptionRepository.findBySubscriberId(user.getId()).forEach(subscription ->
                result.put(subscription.getLocalCollection().getId(), subscription));
        return result;
    }

    public boolean isUpdateAvailable(CollectionSubscription subscription) {
        MockCollection source = subscription.getSourceCollection();
        return source != null && source.isShared() &&
                source.getRevision() > subscription.getSourceRevision();
    }

    public boolean isSourceAvailable(CollectionSubscription subscription) {
        MockCollection source = subscription.getSourceCollection();
        return source != null && source.isShared();
    }

    @Transactional(readOnly = true)
    public MockCollection findSharedSource(Long sourceId, User viewer) {
        MockCollection source = collectionRepository.findSharedById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Shared collection not found."));
        if (source.getOwner().getId().equals(viewer.getId())) {
            throw new IllegalArgumentException("Shared collection not found.");
        }
        return source;
    }

    @Transactional
    public boolean shareCollection(Long collectionId, User owner) {
        return collectionRepository.findByIdAndOwnerId(collectionId, owner.getId())
                .map(collection -> {
                    ensureEditableCollection(collection);
                    ensureShareableCollection(collection);
                    if (!collection.isShared()) {
                        collection.setShared(true);
                        collection.setSharedAt(Instant.now());
                        collectionRepository.save(collection);
                    }
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public boolean unshareCollection(Long collectionId, User owner) {
        return collectionRepository.findByIdAndOwnerId(collectionId, owner.getId())
                .map(collection -> {
                    ensureEditableCollection(collection);
                    if (collection.isShared()) {
                        collection.setShared(false);
                        collection.setSharedAt(null);
                        collectionRepository.save(collection);
                    }
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public MockCollection subscribe(Long sourceId, User subscriber) {
        MockCollection source = findSharedSource(sourceId, subscriber);
        return subscriptionRepository
                .findBySubscriberIdAndSourceCollectionId(subscriber.getId(), source.getId())
                .map(CollectionSubscription::getLocalCollection)
                .orElseGet(() -> createSubscription(source, subscriber));
    }

    @Transactional
    public MockCollection copySharedCollection(Long sourceId, User owner) {
        MockCollection source = findSharedSource(sourceId, owner);
        return copyCollection(source, owner, preferredCopyName(source), false, null);
    }

    @Transactional
    public MockCollection copySubscribedCollection(Long localCollectionId, User owner) {
        CollectionSubscription subscription = subscriptionRepository
                .findBySubscriberIdAndLocalCollectionId(owner.getId(), localCollectionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found."));
        MockCollection local = subscription.getLocalCollection();
        return copyCollection(local, owner, local.getName() + " (copy)", false, null);
    }

    @Transactional
    public MockCollection updateSubscriptionBySource(Long sourceId, User subscriber) {
        CollectionSubscription subscription = subscriptionRepository
                .findBySubscriberIdAndSourceCollectionId(subscriber.getId(), sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found."));
        return updateSubscription(subscription, subscriber);
    }

    @Transactional
    public MockCollection updateSubscriptionByLocal(Long localCollectionId, User subscriber) {
        CollectionSubscription subscription = subscriptionRepository
                .findBySubscriberIdAndLocalCollectionId(subscriber.getId(), localCollectionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found."));
        return updateSubscription(subscription, subscriber);
    }

    @Transactional
    public boolean unsubscribe(Long localCollectionId, User subscriber) {
        return subscriptionRepository
                .findBySubscriberIdAndLocalCollectionId(subscriber.getId(), localCollectionId)
                .map(subscription -> {
                    MockCollection local = subscription.getLocalCollection();
                    mockRepository.deleteByCollectionIdAndOwnerId(local.getId(), subscriber.getId());
                    subscriptionRepository.delete(subscription);
                    collectionRepository.deleteByIdAndOwnerId(local.getId(), subscriber.getId());
                    return true;
                })
                .orElse(false);
    }

    private MockCollection createSubscription(MockCollection source, User subscriber) {
        String preferredName = source.getName();
        if (collectionRepository.existsNameForOwner(subscriber.getId(), preferredName, null)) {
            preferredName = "%s (shared by %s)".formatted(source.getName(), source.getOwner().getUsername());
        }

        MockCollection local = copyCollection(source, subscriber, preferredName, true, source);
        local.setSourceRevision(source.getRevision());
        CollectionSubscription subscription = new CollectionSubscription(
                subscriber, source, local, source.getRevision());
        subscriptionRepository.save(subscription);
        return local;
    }

    private MockCollection updateSubscription(CollectionSubscription subscription, User subscriber) {
        MockCollection source = subscription.getSourceCollection();
        if (source == null || !source.isShared()) {
            throw new IllegalStateException("Source collection is no longer shared.");
        }

        MockCollection local = subscription.getLocalCollection();
        if (!local.isReadOnly() || !local.getOwner().getId().equals(subscriber.getId())) {
            throw new IllegalStateException("Subscription local copy is invalid.");
        }

        mockRepository.deleteByCollectionIdAndOwnerId(local.getId(), subscriber.getId());
        local.setName(uniqueName(subscriber.getId(), source.getName(), local.getId()));
        local.setDescription(source.getDescription());
        local.setSourceCollection(source);
        local.setSourceRevision(source.getRevision());
        collectionRepository.save(local);
        copyMocks(source, subscriber, local);

        subscription.setSourceRevision(source.getRevision());
        subscription.setSourceCollection(source);
        subscriptionRepository.save(subscription);
        return local;
    }

    private MockCollection copyCollection(MockCollection source,
                                          User owner,
                                          String preferredName,
                                          boolean readOnly,
                                          MockCollection sourceCollection) {
        MockCollection local = new MockCollection(
                owner,
                uniqueName(owner.getId(), preferredName, null),
                source.getDescription());
        local.setReadOnly(readOnly);
        local.setSourceCollection(sourceCollection);
        local.setSourceRevision(sourceCollection != null ? sourceCollection.getRevision() : null);
        local = collectionRepository.save(local);
        copyMocks(source, owner, local);
        return local;
    }

    private void copyMocks(MockCollection source, User owner, MockCollection target) {
        List<MockDefinition> sourceMocks = mockRepository.findByCollectionId(source.getId());
        for (MockDefinition sourceMock : sourceMocks) {
            MockDefinition copy = new MockDefinition();
            copy.setOwner(owner);
            copy.setCollection(target);
            copy.setName(sourceMock.getName());
            copy.setHttpMethod(sourceMock.getHttpMethod());
            copy.setPathPattern(sourceMock.getPathPattern());
            copy.setRequestBodyContains(sourceMock.getRequestBodyContains());
            copy.setRequestMatchMode(sourceMock.getRequestMatchMode());
            copy.setRequestMatchGroups(sourceMock.getRequestMatchGroups());
            copy.setResponseStatus(sourceMock.getResponseStatus());
            copy.setResponseBody(sourceMock.getResponseBody());
            copy.setResponseContentType(sourceMock.getResponseContentType());
            copy.setResponseHeaders(sourceMock.getResponseHeaders() != null
                    ? new HashMap<>(sourceMock.getResponseHeaders())
                    : new HashMap<>());
            copy.setPriority(sourceMock.getPriority());
            copy.setActive(sourceMock.isActive());
            mockRepository.save(copy);
        }
    }

    private String preferredCopyName(MockCollection source) {
        return source.getName() + " (copy)";
    }

    private String uniqueName(Long ownerId, String desiredName, Long excludeId) {
        String base = normalizeName(desiredName);
        String candidate = fitName(base, "");
        int counter = 2;
        while (collectionRepository.existsNameForOwner(ownerId, candidate, excludeId)) {
            String suffix = " (" + counter++ + ")";
            candidate = fitName(base, suffix);
        }
        return candidate;
    }

    private String fitName(String base, String suffix) {
        int maxBaseLength = MAX_COLLECTION_NAME_LENGTH - suffix.length();
        String trimmed = base.length() > maxBaseLength ? base.substring(0, maxBaseLength).trim() : base;
        return trimmed + suffix;
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return "Shared collection";
        }
        return name.trim();
    }

    private void ensureEditableCollection(MockCollection collection) {
        if (collection.isReadOnly()) {
            throw new IllegalStateException("Subscribed collections are read-only.");
        }
    }

    private void ensureShareableCollection(MockCollection collection) {
        List<MockDefinition> mocks = mockRepository.findByCollectionId(collection.getId());
        boolean usesUserFunctions = mocks.stream().anyMatch(functionReferenceService::usesUserFunctions);
        if (usesUserFunctions) {
            throw new IllegalStateException("Collections with custom functions cannot be shared.");
        }
    }
}
