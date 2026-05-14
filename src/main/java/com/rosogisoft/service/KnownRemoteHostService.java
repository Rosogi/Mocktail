package com.rosogisoft.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rosogisoft.domain.KnownRemoteHost;
import com.rosogisoft.domain.RequestLog;
import com.rosogisoft.domain.User;
import com.rosogisoft.repository.KnownRemoteHostRepository;
import com.rosogisoft.web.dto.KnownRemoteHostsExportDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnownRemoteHostService {

    private static final String EXPORT_TYPE = "known_hosts";

    private final KnownRemoteHostRepository hostRepository;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Transactional(readOnly = true)
    public List<KnownRemoteHostView> views(User owner) {
        return hostRepository.findByOwnerId(owner.getId()).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public int saveAll(User owner, List<HostInput> inputs) {
        List<HostInput> sanitized = sanitize(inputs, true);
        hostRepository.deleteAllByOwnerId(owner.getId());
        hostRepository.saveAll(sanitized.stream()
                .map(input -> new KnownRemoteHost(
                        owner,
                        input.address(),
                        input.displayName(),
                        input.description()))
                .toList());
        return sanitized.size();
    }

    @Transactional(readOnly = true)
    public byte[] exportAll(User owner) throws IOException {
        KnownRemoteHostsExportDto dto = KnownRemoteHostsExportDto.builder()
                .version(1)
                .type(EXPORT_TYPE)
                .exportedAt(Instant.now().toString())
                .exportedBy(owner.getUsername())
                .hosts(hostRepository.findByOwnerId(owner.getId()).stream()
                        .map(this::toDto)
                        .toList())
                .build();
        return objectMapper.writeValueAsBytes(dto);
    }

    @Transactional
    public ImportResult importFromJson(byte[] data, User owner, String strategy) throws IOException {
        KnownRemoteHostsExportDto dto = objectMapper.readValue(data, KnownRemoteHostsExportDto.class);
        if (dto.getHosts() == null || dto.getHosts().isEmpty()) {
            throw new IllegalArgumentException("This file does not contain known hosts.");
        }
        if (dto.getType() != null && !EXPORT_TYPE.equals(dto.getType())) {
            throw new IllegalArgumentException("This file is not a known hosts export.");
        }

        List<HostInput> imported = sanitize(dto.getHosts().stream()
                .map(host -> new HostInput(host.getAddress(), host.getDisplayName(), host.getDescription()))
                .toList(), true);
        if (imported.isEmpty()) {
            throw new IllegalArgumentException("This file does not contain usable known hosts.");
        }
        String normalizedStrategy = strategy != null ? strategy : "merge";

        Map<String, KnownRemoteHost> existing = hostRepository.findByOwnerId(owner.getId()).stream()
                .collect(Collectors.toMap(
                        host -> normalizeAddress(host.getAddress()),
                        host -> host,
                        (left, right) -> right,
                        LinkedHashMap::new));

        int added = 0;
        int updated = 0;
        for (HostInput input : imported) {
            String key = normalizeAddress(input.address());
            KnownRemoteHost host = existing.get(key);
            if (host == null) {
                hostRepository.save(new KnownRemoteHost(
                        owner,
                        input.address(),
                        input.displayName(),
                        input.description()));
                added++;
                continue;
            }
            if ("skip_existing".equalsIgnoreCase(normalizedStrategy)) {
                continue;
            }
            host.setDisplayName(input.displayName());
            host.setDescription(input.description());
            hostRepository.save(host);
            updated++;
        }
        return new ImportResult(imported.size(), added, updated);
    }

    @Transactional(readOnly = true)
    public Map<String, String> displayNamesByAddress(User owner, Collection<String> addresses) {
        List<String> normalized = normalizeAddresses(addresses);
        if (normalized.isEmpty()) {
            return Map.of();
        }
        return hostRepository.findByOwnerIdAndAddresses(owner.getId(), normalized).stream()
                .collect(Collectors.toMap(
                        host -> normalizeAddress(host.getAddress()),
                        KnownRemoteHost::getDisplayName,
                        (left, right) -> right,
                        LinkedHashMap::new));
    }

    @Transactional(readOnly = true)
    public Optional<String> displayNameForAddress(Long ownerId, String address) {
        if (ownerId == null || address == null || address.isBlank()) {
            return Optional.empty();
        }
        return hostRepository.findByOwnerIdAndAddress(ownerId, normalizeAddress(address))
                .map(KnownRemoteHost::getDisplayName);
    }

    @Transactional(readOnly = true)
    public List<String> findMatchingAddresses(User owner, String search) {
        if (owner == null || search == null || search.isBlank()) {
            return List.of();
        }
        return hostRepository.findMatching(owner.getId(), search.trim()).stream()
                .map(KnownRemoteHost::getAddress)
                .map(this::normalizeAddress)
                .distinct()
                .toList();
    }

    public void annotateLogs(User owner, List<RequestLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        Map<String, String> displayNames = displayNamesByAddress(owner, logs.stream()
                .map(RequestLog::getRemoteAddr)
                .toList());
        logs.forEach(log -> log.setRemoteDisplayName(
                displayNames.get(normalizeAddress(log.getRemoteAddr()))));
    }

    public String exportFilename() {
        return "known-hosts.json";
    }

    private KnownRemoteHostView toView(KnownRemoteHost host) {
        return new KnownRemoteHostView(
                host.getId(),
                host.getAddress(),
                host.getDisplayName(),
                host.getDescription() != null ? host.getDescription() : "");
    }

    private KnownRemoteHostsExportDto.HostDto toDto(KnownRemoteHost host) {
        return KnownRemoteHostsExportDto.HostDto.builder()
                .address(host.getAddress())
                .displayName(host.getDisplayName())
                .description(host.getDescription())
                .build();
    }

    private List<HostInput> sanitize(List<HostInput> inputs, boolean requireDisplayName) {
        if (inputs == null) {
            return List.of();
        }
        Map<String, HostInput> result = new LinkedHashMap<>();
        for (HostInput input : inputs) {
            if (input == null || input.address() == null || input.address().isBlank()) {
                continue;
            }
            String address = normalizeAddress(input.address());
            String displayName = input.displayName() != null ? input.displayName().trim() : "";
            if (requireDisplayName && displayName.isBlank()) {
                throw new IllegalArgumentException("Known host name is required for address \"%s\".".formatted(address));
            }
            if (result.containsKey(address)) {
                throw new IllegalArgumentException("Address \"%s\" appears more than once.".formatted(address));
            }
            result.put(address, new HostInput(
                    address,
                    displayName,
                    input.description() != null ? input.description().trim() : ""));
        }
        return new ArrayList<>(result.values());
    }

    private List<String> normalizeAddresses(Collection<String> addresses) {
        if (addresses == null) {
            return List.of();
        }
        return addresses.stream()
                .filter(address -> address != null && !address.isBlank())
                .map(this::normalizeAddress)
                .distinct()
                .toList();
    }

    private String normalizeAddress(String address) {
        return address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
    }

    public record KnownRemoteHostView(Long id,
                                      String address,
                                      String displayName,
                                      String description) {
    }

    public record HostInput(String address,
                            String displayName,
                            String description) {
    }

    public record ImportResult(int processed, int added, int updated) {
    }
}
