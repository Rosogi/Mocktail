package com.rosogisoft.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rosogisoft.domain.KnownRemoteHost;
import com.rosogisoft.domain.RequestLog;
import com.rosogisoft.domain.User;
import com.rosogisoft.repository.KnownRemoteHostRepository;
import com.rosogisoft.web.dto.KnownRemoteHostsExportDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class KnownRemoteHostServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exportsKnownHostsAsPortableJson() throws Exception {
        User owner = owner();
        RepositoryState state = new RepositoryState(owner);
        state.hosts.add(host(owner, "10.10.0.15", "Billing QA", "test client"));
        KnownRemoteHostService service = service(state);

        KnownRemoteHostsExportDto exported = objectMapper.readValue(
                service.exportAll(owner),
                KnownRemoteHostsExportDto.class);

        assertThat(exported.getType()).isEqualTo("known_hosts");
        assertThat(exported.getHosts()).hasSize(1);
        assertThat(exported.getHosts().getFirst().getAddress()).isEqualTo("10.10.0.15");
        assertThat(exported.getHosts().getFirst().getDisplayName()).isEqualTo("Billing QA");
    }

    @Test
    void mergeImportUpdatesExistingAddressesAndAddsNewOnes() throws Exception {
        User owner = owner();
        RepositoryState state = new RepositoryState(owner);
        state.hosts.add(host(owner, "10.10.0.15", "Old name", "old"));
        KnownRemoteHostService service = service(state);

        KnownRemoteHostsExportDto payload = exportDto(
                hostDto("10.10.0.15", "Billing QA", "updated"),
                hostDto("10.10.0.16", "Orders QA", ""));

        KnownRemoteHostService.ImportResult result =
                service.importFromJson(objectMapper.writeValueAsBytes(payload), owner, "merge");

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(state.hosts).hasSize(2);
        assertThat(state.hosts.getFirst().getDisplayName()).isEqualTo("Billing QA");
    }

    @Test
    void skipExistingImportKeepsStoredValue() throws Exception {
        User owner = owner();
        RepositoryState state = new RepositoryState(owner);
        state.hosts.add(host(owner, "10.10.0.15", "Original", ""));
        KnownRemoteHostService service = service(state);

        KnownRemoteHostsExportDto payload = exportDto(
                hostDto("10.10.0.15", "Imported", "should not replace"));

        KnownRemoteHostService.ImportResult result =
                service.importFromJson(objectMapper.writeValueAsBytes(payload), owner, "skip_existing");

        assertThat(result.added()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(state.hosts.getFirst().getDisplayName()).isEqualTo("Original");
    }

    @Test
    void resolvesDisplayNamesAndFindsAddressesByName() {
        User owner = owner();
        RepositoryState state = new RepositoryState(owner);
        state.hosts.add(host(owner, "10.10.0.15", "Billing QA", "test client"));
        KnownRemoteHostService service = service(state);

        RequestLog log = new RequestLog();
        log.setRemoteAddr("10.10.0.15");
        service.annotateLogs(owner, List.of(log));

        assertThat(log.getRemoteDisplayName()).isEqualTo("Billing QA");
        assertThat(service.findMatchingAddresses(owner, "billing")).containsExactly("10.10.0.15");
    }

    private KnownRemoteHostService service(RepositoryState state) {
        return new KnownRemoteHostService(repository(state));
    }

    private KnownRemoteHostRepository repository(RepositoryState state) {
        return (KnownRemoteHostRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{KnownRemoteHostRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByOwnerId" -> List.copyOf(state.hosts);
                    case "deleteAllByOwnerId" -> {
                        int size = state.hosts.size();
                        state.hosts.clear();
                        yield size;
                    }
                    case "saveAll" -> {
                        Iterable<?> values = (Iterable<?>) args[0];
                        for (Object value : values) {
                            state.hosts.add((KnownRemoteHost) value);
                        }
                        yield List.copyOf(state.hosts);
                    }
                    case "save" -> {
                        KnownRemoteHost saved = (KnownRemoteHost) args[0];
                        int existingIndex = indexOfAddress(state.hosts, saved.getAddress());
                        if (existingIndex >= 0) {
                            state.hosts.set(existingIndex, saved);
                        } else {
                            state.hosts.add(saved);
                        }
                        yield saved;
                    }
                    case "findByOwnerIdAndAddress" -> state.hosts.stream()
                            .filter(host -> normalize(host.getAddress()).equals(normalize((String) args[1])))
                            .findFirst();
                    case "findByOwnerIdAndAddresses" -> {
                        @SuppressWarnings("unchecked")
                        List<String> addresses = (List<String>) args[1];
                        yield state.hosts.stream()
                                .filter(host -> addresses.contains(normalize(host.getAddress())))
                                .toList();
                    }
                    case "findMatching" -> {
                        String search = normalize((String) args[1]);
                        yield state.hosts.stream()
                                .filter(host ->
                                        normalize(host.getAddress()).contains(search) ||
                                                normalize(host.getDisplayName()).contains(search) ||
                                                normalize(host.getDescription()).contains(search))
                                .toList();
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private int indexOfAddress(List<KnownRemoteHost> hosts, String address) {
        for (int index = 0; index < hosts.size(); index++) {
            if (normalize(hosts.get(index).getAddress()).equals(normalize(address))) {
                return index;
            }
        }
        return -1;
    }

    private KnownRemoteHostsExportDto exportDto(KnownRemoteHostsExportDto.HostDto... hosts) {
        return KnownRemoteHostsExportDto.builder()
                .version(1)
                .type("known_hosts")
                .hosts(List.of(hosts))
                .build();
    }

    private KnownRemoteHostsExportDto.HostDto hostDto(String address, String displayName, String description) {
        return KnownRemoteHostsExportDto.HostDto.builder()
                .address(address)
                .displayName(displayName)
                .description(description)
                .build();
    }

    private KnownRemoteHost host(User owner, String address, String displayName, String description) {
        return new KnownRemoteHost(owner, address, displayName, description);
    }

    private User owner() {
        User owner = new User("alice");
        owner.setId(1L);
        return owner;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static class RepositoryState {
        private final User owner;
        private final List<KnownRemoteHost> hosts = new ArrayList<>();

        private RepositoryState(User owner) {
            this.owner = owner;
        }
    }
}
