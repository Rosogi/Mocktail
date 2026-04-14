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
            c.setName(name);
            c.setDescription(description);
            return collectionRepository.save(c);
        });
    }

    @Transactional
    public boolean delete(Long id, User owner) {
        // Mocks in this collection will have collection_id set to NULL (ON DELETE SET NULL)
        return collectionRepository.deleteByIdAndOwnerId(id, owner.getId()) > 0;
    }

    @Transactional
    public boolean enableAll(Long collectionId, User owner) {
        return mockRepository.setActiveForCollection(collectionId, owner.getId(), true) >= 0;
    }

    @Transactional
    public boolean disableAll(Long collectionId, User owner) {
        return mockRepository.setActiveForCollection(collectionId, owner.getId(), false) >= 0;
    }

    @Transactional
    public void addMock(Long collectionId, Long mockId, User owner) {
        collectionRepository.findByIdAndOwnerId(collectionId, owner.getId())
                .ifPresent(collection ->
                        mockRepository.findByIdAndOwnerId(mockId, owner.getId())
                                .ifPresent(mock -> {
                                    mock.setCollection(collection);
                                    mockRepository.save(mock);
                                }));
    }

    @Transactional
    public void removeMock(Long collectionId, Long mockId, User owner) {
        mockRepository.findByIdAndOwnerId(mockId, owner.getId())
                .ifPresent(mock -> {
                    if (mock.getCollection() != null &&
                            mock.getCollection().getId().equals(collectionId)) {
                        mock.setCollection(null);
                        mockRepository.save(mock);
                    }
                });
    }

    public int countMocks(Long collectionId) {
        return mockRepository.findByCollectionId(collectionId).size();
    }
}