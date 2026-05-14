package com.rosogisoft.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rosogisoft.domain.MockCollection;
import com.rosogisoft.domain.MockDefinition;
import com.rosogisoft.domain.User;
import com.rosogisoft.repository.MockCollectionRepository;
import com.rosogisoft.repository.MockDefinitionRepository;
import com.rosogisoft.web.dto.ImportMode;
import com.rosogisoft.web.dto.MockExportDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockImportExportServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exportsSingleAdvancedMockWithMatchingSettings() throws Exception {
        User owner = owner();
        MockDefinition mock = mockDefinition(owner, "Advanced", "advanced", "[{\"conditions\":[{\"source\":\"header\"}]}]");
        MockImportExportService service = service(new RepositoryState());

        MockExportDto exported = objectMapper.readValue(
                service.exportSingleMock(mock, owner),
                MockExportDto.class);

        assertThat(exported.getMocks()).hasSize(1);
        assertThat(exported.getMocks().getFirst().getRequestMatchMode()).isEqualTo("advanced");
        assertThat(exported.getMocks().getFirst().getRequestMatchGroups())
                .isEqualTo("[{\"conditions\":[{\"source\":\"header\"}]}]");
    }

    @Test
    void exportsCollectionsAndLooseMocksTogether() throws Exception {
        User owner = owner();
        MockCollection collection = new MockCollection(owner, "Payments", "Collection export");
        collection.setId(10L);

        MockDefinition advanced = mockDefinition(owner, "Advanced", "advanced", "[{\"connector\":\"and\"}]");
        advanced.setCollection(collection);
        MockDefinition basic = mockDefinition(owner, "Basic", "basic", null);

        RepositoryState state = new RepositoryState();
        state.ownerMocks.addAll(List.of(advanced, basic));
        state.ownerCollections.add(collection);
        MockImportExportService service = service(state);

        MockExportDto exported = objectMapper.readValue(service.exportAll(owner), MockExportDto.class);

        assertThat(exported.getCollections()).hasSize(1);
        assertThat(exported.getCollections().getFirst().getMocks()).hasSize(1);
        assertThat(exported.getCollections().getFirst().getMocks().getFirst().getRequestMatchMode())
                .isEqualTo("advanced");
        assertThat(exported.getMocks()).hasSize(1);
        assertThat(exported.getMocks().getFirst().getRequestMatchMode()).isEqualTo("basic");
    }

    @Test
    void exportsSingleCollectionWithAdvancedMocks() throws Exception {
        User owner = owner();
        MockCollection collection = new MockCollection(owner, "Profiles", "Single collection export");
        collection.setId(22L);

        MockDefinition advanced = mockDefinition(owner, "Profile search", "advanced", "[{\"source\":\"json_body\"}]");
        advanced.setCollection(collection);

        RepositoryState state = new RepositoryState();
        state.mocksByCollection.put(collection.getId(), List.of(advanced));
        MockImportExportService service = service(state);

        MockExportDto exported = objectMapper.readValue(
                service.exportCollection(collection, owner),
                MockExportDto.class);

        assertThat(exported.getCollections()).hasSize(1);
        assertThat(exported.getCollections().getFirst().getName()).isEqualTo("Profiles");
        assertThat(exported.getCollections().getFirst().getMocks()).hasSize(1);
        assertThat(exported.getCollections().getFirst().getMocks().getFirst().getRequestMatchMode())
                .isEqualTo("advanced");
    }

    @Test
    void importsAdvancedCollectionMockAndBasicLooseMockWithExpectedModes() throws Exception {
        User owner = owner();
        RepositoryState state = new RepositoryState();
        MockImportExportService service = service(state);

        MockExportDto collectionsFile = MockExportDto.builder()
                .collections(List.of(MockExportDto.CollectionDto.builder()
                        .name("Imported collection")
                        .description("Advanced data")
                        .mocks(List.of(MockExportDto.MockDto.builder()
                                .name("Advanced")
                                .httpMethod("POST")
                                .pathPattern("/advanced")
                                .requestMatchMode("advanced")
                                .requestMatchGroups("[{\"connector\":\"or\"}]")
                                .responseStatus(202)
                                .responseContentType("application/json")
                                .priority(7)
                                .active(true)
                                .build()))
                        .build()))
                .mocks(List.of())
                .build();

        service.importFromJson(objectMapper.writeValueAsBytes(collectionsFile), owner, ImportMode.COLLECTIONS_ONLY);

        MockExportDto looseMocksFile = MockExportDto.builder()
                .collections(List.of())
                .mocks(List.of(MockExportDto.MockDto.builder()
                        .name("Legacy basic")
                        .httpMethod("GET")
                        .pathPattern("/basic")
                        .responseStatus(200)
                        .responseContentType("application/json")
                        .priority(0)
                        .active(true)
                        .build()))
                .build();

        service.importFromJson(objectMapper.writeValueAsBytes(looseMocksFile), owner, ImportMode.MOCKS_ONLY);

        assertThat(state.savedMocks).hasSize(2);
        assertThat(state.savedMocks.get(0).getRequestMatchMode()).isEqualTo("advanced");
        assertThat(state.savedMocks.get(0).getRequestMatchGroups()).isEqualTo("[{\"connector\":\"or\"}]");
        assertThat(state.savedMocks.get(0).getCollection()).isNotNull();
        assertThat(state.savedMocks.get(1).getRequestMatchMode()).isEqualTo("basic");
        assertThat(state.savedMocks.get(1).getRequestMatchGroups()).isNull();
    }

    @Test
    void rejectsCollectionFileOnMocksPageEvenWhenCollectionIsEmpty() throws Exception {
        User owner = owner();
        MockImportExportService service = service(new RepositoryState());
        MockExportDto dto = MockExportDto.builder()
                .collections(List.of(MockExportDto.CollectionDto.builder()
                        .name("Empty collection")
                        .description("Still a collection file")
                        .mocks(List.of())
                        .build()))
                .mocks(List.of())
                .build();

        assertThatThrownBy(() -> service.importFromJson(
                objectMapper.writeValueAsBytes(dto),
                owner,
                ImportMode.MOCKS_ONLY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collections");
    }

    private MockImportExportService service(RepositoryState state) {
        return new MockImportExportService(mockRepository(state), collectionRepository(state));
    }

    private MockDefinitionRepository mockRepository(RepositoryState state) {
        return (MockDefinitionRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{MockDefinitionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByOwnerId" -> List.copyOf(state.ownerMocks);
                    case "findByCollectionId" ->
                            List.copyOf(state.mocksByCollection.getOrDefault((Long) args[0], List.of()));
                    case "save" -> {
                        MockDefinition mock = (MockDefinition) args[0];
                        state.savedMocks.add(mock);
                        yield mock;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private MockCollectionRepository collectionRepository(RepositoryState state) {
        return (MockCollectionRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{MockCollectionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByOwnerId" -> List.copyOf(state.ownerCollections);
                    case "save" -> {
                        MockCollection collection = (MockCollection) args[0];
                        if (collection.getId() == null) {
                            collection.setId(state.nextCollectionId++);
                        }
                        state.savedCollections.add(collection);
                        if (state.ownerCollections.stream()
                                .noneMatch(existing -> existing.getId() != null &&
                                        existing.getId().equals(collection.getId()))) {
                            state.ownerCollections.add(collection);
                        }
                        yield collection;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private User owner() {
        User owner = new User("alice");
        owner.setId(1L);
        return owner;
    }

    private MockDefinition mockDefinition(User owner, String name, String mode, String groups) {
        MockDefinition mock = new MockDefinition();
        mock.setOwner(owner);
        mock.setName(name);
        mock.setHttpMethod("GET");
        mock.setPathPattern("/" + name.toLowerCase());
        mock.setRequestMatchMode(mode);
        mock.setRequestMatchGroups(groups);
        mock.setResponseStatus(200);
        mock.setResponseContentType("application/json");
        mock.setPriority(5);
        mock.setActive(true);
        return mock;
    }

    private static class RepositoryState {
        private final List<MockDefinition> ownerMocks = new ArrayList<>();
        private final List<MockCollection> ownerCollections = new ArrayList<>();
        private final Map<Long, List<MockDefinition>> mocksByCollection = new HashMap<>();
        private final List<MockDefinition> savedMocks = new ArrayList<>();
        private final List<MockCollection> savedCollections = new ArrayList<>();
        private long nextCollectionId = 100L;
    }
}
