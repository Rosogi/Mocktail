package com.rosogisoft.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rosogisoft.domain.MockCollection;
import com.rosogisoft.domain.MockDefinition;
import com.rosogisoft.domain.User;
import com.rosogisoft.web.dto.MockExportDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockImportExportService {

    private final MockDefinitionRepository  mockRepository;
    private final MockCollectionRepository  collectionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    // ── Export ────────────────────────────────────────────────────────

    /** Export ALL mocks (and collections) for a user */
    public byte[] exportAll(User owner) throws IOException {
        return objectMapper.writeValueAsBytes(buildExport(owner, null));
    }

    /** Export a single collection */
    public byte[] exportCollection(MockCollection collection, User owner) throws IOException {
        MockExportDto dto = MockExportDto.builder()
                .version(1)
                .exportedAt(Instant.now().toString())
                .exportedBy(owner.getUsername())
                .collections(List.of(toCollectionDto(collection)))
                .mocks(List.of())
                .build();
        return objectMapper.writeValueAsBytes(dto);
    }

    private MockExportDto buildExport(User owner, Long collectionId) {
        List<MockDefinition> all = collectionId != null
                ? mockRepository.findByCollectionId(collectionId)
                : mockRepository.findByOwnerId(owner.getId());

        // Group by collection
        List<MockCollection> collections = collectionRepository.findByOwnerId(owner.getId());

        List<MockExportDto.CollectionDto> collDtos = collections.stream()
                .map(c -> {
                    List<MockDefinition> mocks = all.stream()
                            .filter(m -> m.getCollection() != null && m.getCollection().getId().equals(c.getId()))
                            .collect(Collectors.toList());
                    return MockExportDto.CollectionDto.builder()
                            .name(c.getName())
                            .description(c.getDescription())
                            .mocks(mocks.stream().map(this::toMockDto).collect(Collectors.toList()))
                            .build();
                })
                .collect(Collectors.toList());

        List<MockExportDto.MockDto> uncollected = all.stream()
                .filter(m -> m.getCollection() == null)
                .map(this::toMockDto)
                .collect(Collectors.toList());

        return MockExportDto.builder()
                .version(1)
                .exportedAt(Instant.now().toString())
                .exportedBy(owner.getUsername())
                .collections(collDtos)
                .mocks(uncollected)
                .build();
    }

    private MockExportDto.CollectionDto toCollectionDto(MockCollection c) {
        List<MockDefinition> mocks = mockRepository.findByCollectionId(c.getId());
        return MockExportDto.CollectionDto.builder()
                .name(c.getName())
                .description(c.getDescription())
                .mocks(mocks.stream().map(this::toMockDto).collect(Collectors.toList()))
                .build();
    }

    private MockExportDto.MockDto toMockDto(MockDefinition m) {
        return MockExportDto.MockDto.builder()
                .name(m.getName())
                .httpMethod(m.getHttpMethod())
                .pathPattern(m.getPathPattern())
                .requestBodyContains(m.getRequestBodyContains())
                .responseStatus(m.getResponseStatus())
                .responseBody(m.getResponseBody())
                .responseContentType(m.getResponseContentType())
                .responseHeaders(m.getResponseHeaders())
                .priority(m.getPriority())
                .active(m.isActive())
                .build();
    }

    // ── Import ────────────────────────────────────────────────────────

    @Transactional
    public ImportResult importFromJson(byte[] data, User owner) throws IOException {
        MockExportDto dto = objectMapper.readValue(data, MockExportDto.class);
        int mocks = 0;
        int collections = 0;

        // Import collections + their mocks
        if (dto.getCollections() != null) {
            for (MockExportDto.CollectionDto cDto : dto.getCollections()) {
                MockCollection collection = collectionRepository
                        .findByOwnerId(owner.getId()).stream()
                        .filter(c -> c.getName().equals(cDto.getName()))
                        .findFirst()
                        .orElseGet(() -> {
                            MockCollection c = new MockCollection(owner, cDto.getName(), cDto.getDescription());
                            return collectionRepository.save(c);
                        });
                collections++;

                if (cDto.getMocks() != null) {
                    for (MockExportDto.MockDto mDto : cDto.getMocks()) {
                        mockRepository.save(fromDto(mDto, owner, collection));
                        mocks++;
                    }
                }
            }
        }

        // Import uncollected mocks
        if (dto.getMocks() != null) {
            for (MockExportDto.MockDto mDto : dto.getMocks()) {
                mockRepository.save(fromDto(mDto, owner, null));
                mocks++;
            }
        }

        log.info("Imported {} mocks in {} collections for user '{}'", mocks, collections, owner.getUsername());
        return new ImportResult(mocks, collections);
    }

    private MockDefinition fromDto(MockExportDto.MockDto dto, User owner, MockCollection collection) {
        MockDefinition m = new MockDefinition();
        m.setOwner(owner);
        m.setCollection(collection);
        m.setName(dto.getName());
        m.setHttpMethod(dto.getHttpMethod());
        m.setPathPattern(dto.getPathPattern());
        m.setRequestBodyContains(dto.getRequestBodyContains());
        m.setResponseStatus(dto.getResponseStatus());
        m.setResponseBody(dto.getResponseBody());
        m.setResponseContentType(dto.getResponseContentType());
        m.setResponseHeaders(dto.getResponseHeaders() != null ? dto.getResponseHeaders() : new java.util.HashMap<>());
        m.setPriority(dto.getPriority());
        m.setActive(dto.isActive());
        return m;
    }

    public record ImportResult(int mocks, int collections) {}
}