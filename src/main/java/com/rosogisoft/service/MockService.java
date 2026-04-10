package com.rosogisoft.service;

import com.rosogisoft.domain.MockDefinition;
import com.rosogisoft.domain.User;
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

    public List<MockDefinition> findAllForUser (User user) {
        return mockRepository.findByOwnerId(user.getId());
    }

    public Optional<MockDefinition> findByIdForUser (Long id, User user) {
        return mockRepository.findByIdAndOwnerId(id, user.getId());
    }

    @Transactional
    public MockDefinition create (MockDefinitionForm form, User owner) {
        MockDefinition mock = new MockDefinition();
        mock.setOwner(owner);
        applyForm(mock, form);
        return mockRepository.save(mock);
    }

    @Transactional
    public Optional<MockDefinition> update (Long id, MockDefinitionForm form, User owner) {
        return mockRepository.findByIdAndOwnerId(id, owner.getId()).map(mock -> {
            applyForm(mock, form);
            return mockRepository.save(mock);
        });
    }

    @Transactional
    public boolean toggleActive (Long id, User owner) {
        return mockRepository.toggleActive(id, owner.getId()) > 0;
    }

    @Transactional
    public boolean delete (Long id, User owner) {
        return mockRepository.deleteByIdAndOwnerId(id, owner.getId()) > 0;
    }

    private void applyForm (MockDefinition mock, MockDefinitionForm form) {
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
    }
}
