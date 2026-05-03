package com.rosogisoft.service;

import com.rosogisoft.domain.MockCollection;
import com.rosogisoft.domain.User;
import com.rosogisoft.repository.MockCollectionRepository;
import com.rosogisoft.repository.MockDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MockCollectionService {

    private final MockCollectionRepository collectionRepository;
    private final MockDefinitionRepository mockRepository;

    public List<MockCollection> findAllForUser(User user) {
        return collectionRepository.findByOwnerId(user.getId());
    }

    public List<MockCollection> findEditableForUser(User user) {
        return collectionRepository.findEditableByOwnerId(user.getId());
    }

    public Optional<MockCollection> findByIdForUser(Long id, User user) {
        return collectionRepository.findByIdAndOwnerId(id, user.getId());
    }

    @Transactional
    public MockCollection create(String name, String description, User owner) {
        return collectionRepository.save(new MockCollection(owner, name, description));
    }

    @Transactional
    public Optional<MockCollection> update(Long id, String name, String description, User owner) {
        return collectionRepository.findByIdAndOwnerId(id, owner.getId()).map(c -> {
            ensureEditable(c);
            c.setName(name);
            c.setDescription(description);
            c.setRevision(c.getRevision() + 1);
            return collectionRepository.save(c);
        });
    }

    @Transactional
    public boolean delete(Long id, User owner) {
        return collectionRepository.findByIdAndOwnerId(id, owner.getId())
                .map(collection -> {
                    ensureEditable(collection);
                    // Mocks in this collection will have collection_id set to NULL (ON DELETE SET NULL)
                    return collectionRepository.deleteByIdAndOwnerId(id, owner.getId()) > 0;
                })
                .orElse(false);
    }

    @Transactional
    public boolean enableAll(Long collectionId, User owner) {
        return setActiveForCollection(collectionId, owner, true);
    }

    @Transactional
    public boolean disableAll(Long collectionId, User owner) {
        return setActiveForCollection(collectionId, owner, false);
    }

    @Transactional
    public void addMock(Long collectionId, Long mockId, User owner) {
        collectionRepository.findByIdAndOwnerId(collectionId, owner.getId())
                .ifPresent(collection -> {
                    ensureEditable(collection);
                    mockRepository.findByIdAndOwnerId(mockId, owner.getId())
                            .ifPresent(mock -> {
                                if (mock.getCollection() != null) {
                                    ensureEditable(mock.getCollection());
                                }
                                Long previousCollectionId = mock.getCollection() != null
                                        ? mock.getCollection().getId()
                                        : null;
                                mock.setCollection(collection);
                                mockRepository.save(mock);
                                touchCollection(collection);
                                if (previousCollectionId != null &&
                                        !previousCollectionId.equals(collection.getId())) {
                                    touchCollectionById(previousCollectionId);
                                }
                            });
                });
    }

    @Transactional
    public void removeMock(Long collectionId, Long mockId, User owner) {
        mockRepository.findByIdAndOwnerId(mockId, owner.getId())
                .ifPresent(mock -> {
                    if (mock.getCollection() != null &&
                            mock.getCollection().getId().equals(collectionId)) {
                        ensureEditable(mock.getCollection());
                        mock.setCollection(null);
                        mockRepository.save(mock);
                        touchCollectionById(collectionId);
                    }
                });
    }

    public int countMocks(Long collectionId) {
        return (int) mockRepository.countByCollectionId(collectionId);
    }

    private boolean setActiveForCollection(Long collectionId, User owner, boolean active) {
        return collectionRepository.findByIdAndOwnerId(collectionId, owner.getId())
                .map(collection -> {
                    mockRepository.setActiveForCollection(collectionId, owner.getId(), active);
                    touchCollection(collection);
                    return true;
                })
                .orElse(false);
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

    private void ensureEditable(MockCollection collection) {
        if (collection.isReadOnly()) {
            throw new IllegalStateException("Subscribed collections are read-only.");
        }
    }
}
