package com.rosogisoft.service;

import com.rosogisoft.domain.MockCollection;
import com.rosogisoft.domain.MockDefinition;
import com.rosogisoft.domain.User;
import com.rosogisoft.repository.MockCollectionRepository;
import com.rosogisoft.repository.MockDefinitionRepository;
import com.rosogisoft.web.dto.MockDefinitionForm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MockService {

    private final MockDefinitionRepository mockRepository;
    private final MockCollectionRepository collectionRepository;

    public List<MockDefinition> findAllForUser(User user) {
        return mockRepository.findByOwnerId(user.getId());
    }

    public Optional<MockDefinition> findByIdForUser(Long id, User user) {
        return mockRepository.findByIdAndOwnerId(id, user.getId());
    }

    @Transactional
    public MockDefinition create(MockDefinitionForm form, User owner) {
        MockDefinition mock = new MockDefinition();
        mock.setOwner(owner);
        applyForm(mock, form, owner);
        MockDefinition saved = mockRepository.save(mock);
        touchCollection(saved.getCollection());
        return saved;
    }

    @Transactional
    public Optional<MockDefinition> update(Long id, MockDefinitionForm form, User owner) {
        return mockRepository.findByIdAndOwnerId(id, owner.getId()).map(mock -> {
            ensureEditable(mock);
            Long previousCollectionId = mock.getCollection() != null
                    ? mock.getCollection().getId()
                    : null;
            applyForm(mock, form, owner);
            MockDefinition saved = mockRepository.save(mock);
            touchCollection(saved.getCollection());
            if (previousCollectionId != null &&
                    (saved.getCollection() == null ||
                            !previousCollectionId.equals(saved.getCollection().getId()))) {
                touchCollectionById(previousCollectionId);
            }
            return saved;
        });
    }

    @Transactional
    public boolean toggleActive(Long id, User owner) {
        return mockRepository.findByIdAndOwnerId(id, owner.getId())
                .map(mock -> {
                    boolean updated = mockRepository.toggleActive(id, owner.getId()) > 0;
                    touchCollection(mock.getCollection());
                    return updated;
                })
                .orElse(false);
    }

    @Transactional
    public boolean delete(Long id, User owner) {
        return mockRepository.findByIdAndOwnerId(id, owner.getId())
                .map(mock -> {
                    ensureEditable(mock);
                    Long collectionId = mock.getCollection() != null
                            ? mock.getCollection().getId()
                            : null;
                    boolean deleted = mockRepository.deleteByIdAndOwnerId(id, owner.getId()) > 0;
                    touchCollectionById(collectionId);
                    return deleted;
                })
                .orElse(false);
    }

    private void applyForm(MockDefinition mock, MockDefinitionForm form, User owner) {
        mock.setName(form.getName());
        mock.setHttpMethod(form.getHttpMethod().toUpperCase());
        mock.setPathPattern(form.getPathPattern());
        mock.setRequestBodyContains(form.getRequestBodyContains());
        mock.setResponseStatus(form.getResponseStatus());
        mock.setResponseBody(form.getResponseBody());
        mock.setResponseContentType(form.getResponseContentType());
        mock.setResponseHeaders(form.getParsedHeaders());
        mock.setPriority(form.getPriority());
        mock.setActive(form.isActive());

        // Assign collection (verify ownership)
        if (form.getCollectionId() != null) {
            MockCollection col = collectionRepository
                    .findByIdAndOwnerId(form.getCollectionId(), owner.getId())
                    .orElse(null);
            if (col != null && col.isReadOnly()) {
                throw new IllegalStateException("Subscribed collections are read-only.");
            }
            mock.setCollection(col);
        } else {
            mock.setCollection(null);
        }
    }

    private void ensureEditable(MockDefinition mock) {
        if (mock.getCollection() != null && mock.getCollection().isReadOnly()) {
            throw new IllegalStateException("Subscribed collections are read-only.");
        }
    }

    private void touchCollection(MockCollection collection) {
        if (collection != null && !collection.isReadOnly()) {
            collection.setRevision(collection.getRevision() + 1);
            collectionRepository.save(collection);
        }
    }

    private void touchCollectionById(Long collectionId) {
        if (collectionId != null) {
            collectionRepository.incrementRevision(collectionId);
        }
    }
}
