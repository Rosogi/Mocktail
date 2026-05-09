package com.rosogisoft.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rosogisoft.domain.EnvironmentPackage;
import com.rosogisoft.domain.EnvironmentVariable;
import com.rosogisoft.domain.SettingKey;
import com.rosogisoft.domain.User;
import com.rosogisoft.repository.EnvironmentPackageRepository;
import com.rosogisoft.repository.EnvironmentVariableRepository;
import com.rosogisoft.web.dto.EnvironmentExportDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnvironmentService {

    public static final String REQUEST_HEADER = "X-Mocktail-Environment";
    public static final String REQUEST_QUERY_PARAM = "__mocktail_env";

    private static final int MAX_PACKAGE_NAME_LENGTH = 255;

    private final EnvironmentPackageRepository packageRepository;
    private final EnvironmentVariableRepository variableRepository;
    private final UserSettingsService settingsService;
    private final EnvironmentTemplateService templateService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Transactional(readOnly = true)
    public List<EnvironmentPackageView> packageViews(User owner) {
        Long activeId = activePackageId(owner).orElse(null);
        return packageRepository.findByOwnerId(owner.getId()).stream()
                .map(environment -> toPackageView(environment, activeId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EnvironmentVariableView> globalViews(User owner) {
        List<EnvironmentVariable> globals = variableRepository.findGlobalsByOwnerId(owner.getId());
        Map<String, String> resolved = templateService.resolveGlobals(toRawMap(globals));
        return globals.stream()
                .map(variable -> toVariableView(variable, resolved.get(variable.getKey())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TemplateSuggestion> templateSuggestions(User owner) {
        List<EnvironmentVariable> globals = variableRepository.findGlobalsByOwnerId(owner.getId());
        Map<String, String> rawGlobals = toRawMap(globals);
        Map<String, String> resolvedGlobals = templateService.resolveGlobals(rawGlobals);
        Map<String, EnvironmentVariable> globalsByKey = globals.stream()
                .collect(Collectors.toMap(
                        EnvironmentVariable::getKey,
                        variable -> variable,
                        (left, right) -> right,
                        LinkedHashMap::new));

        List<TemplateSuggestion> suggestions = new ArrayList<>();
        globals.forEach(variable -> suggestions.add(toSuggestion(
                "global", variable.getKey(), resolvedGlobals.get(variable.getKey()), variable.isHidden())));

        Optional<EnvironmentPackage> activeEnvironment = activePackageId(owner)
                .flatMap(id -> packageRepository.findByIdAndOwnerId(id, owner.getId()));
        if (activeEnvironment.isPresent()) {
            List<EnvironmentVariable> variables =
                    variableRepository.findByOwnerIdAndPackageId(owner.getId(), activeEnvironment.get().getId());
            Map<String, String> resolvedVariables = templateService.resolveVariables(toRawMap(variables), rawGlobals);
            variables.forEach(variable -> suggestions.add(toSuggestion(
                    "env", variable.getKey(), resolvedVariables.get(variable.getKey()), variable.isHidden())));

            Map<String, EnvironmentVariable> variablesByKey = variables.stream()
                    .collect(Collectors.toMap(
                            EnvironmentVariable::getKey,
                            variable -> variable,
                            (left, right) -> right,
                            LinkedHashMap::new));
            globalsByKey.forEach((key, variable) -> {
                if (!variablesByKey.containsKey(key)) {
                    suggestions.add(toSuggestion("env", key, resolvedGlobals.get(key), variable.isHidden()));
                }
            });
        } else {
            globals.forEach(variable -> suggestions.add(toSuggestion(
                    "env", variable.getKey(), resolvedGlobals.get(variable.getKey()), variable.isHidden())));
        }
        return suggestions;
    }

    @Transactional(readOnly = true)
    public Optional<EnvironmentPackageView> packageView(Long id, User owner) {
        Long activeId = activePackageId(owner).orElse(null);
        return packageRepository.findByIdAndOwnerId(id, owner.getId())
                .map(environment -> toPackageView(environment, activeId));
    }

    @Transactional
    public EnvironmentPackage createPackage(String name,
                                            String description,
                                            boolean makeActive,
                                            User owner) {
        EnvironmentPackage environment = packageRepository.save(
                new EnvironmentPackage(owner, uniquePackageName(owner.getId(), normalizeName(name), null), description));
        if (makeActive) {
            setActiveEnvironment(owner, environment.getId());
        }
        return environment;
    }

    @Transactional
    public Optional<EnvironmentPackage> duplicatePackage(Long sourceId,
                                                         String newName,
                                                         User owner) {
        return packageRepository.findByIdAndOwnerId(sourceId, owner.getId())
                .map(source -> {
                    EnvironmentPackage copy = packageRepository.save(new EnvironmentPackage(
                            owner,
                            uniquePackageName(owner.getId(), normalizeName(newName), null),
                            source.getDescription()));
                    List<EnvironmentVariable> variables =
                            variableRepository.findByOwnerIdAndPackageId(owner.getId(), source.getId());
                    variableRepository.saveAll(variables.stream()
                            .map(variable -> copyVariable(variable, owner, copy))
                            .toList());
                    return copy;
                });
    }

    @Transactional
    public boolean deletePackage(Long id, User owner) {
        boolean deleted = packageRepository.deleteByIdAndOwnerId(id, owner.getId()) > 0;
        if (deleted && activePackageId(owner).filter(activeId -> activeId.equals(id)).isPresent()) {
            settingsService.set(owner, SettingKey.ACTIVE_ENVIRONMENT_ID, "");
        }
        return deleted;
    }

    @Transactional
    public void saveGlobals(User owner, List<VariableInput> variables) {
        variableRepository.deleteGlobalsByOwnerId(owner.getId());
        variableRepository.saveAll(toEntities(owner, null, variables));
    }

    @Transactional
    public Optional<EnvironmentPackage> savePackageVariables(Long packageId,
                                                             User owner,
                                                             List<VariableInput> variables) {
        return packageRepository.findByIdAndOwnerId(packageId, owner.getId())
                .map(environment -> {
                    variableRepository.deleteByOwnerIdAndPackageId(owner.getId(), environment.getId());
                    variableRepository.saveAll(toEntities(owner, environment, variables));
                    environment.setUpdatedAt(Instant.now());
                    return packageRepository.save(environment);
                });
    }

    @Transactional
    public void setActiveEnvironment(User owner, Long environmentId) {
        if (environmentId == null) {
            settingsService.set(owner, SettingKey.ACTIVE_ENVIRONMENT_ID, "");
            return;
        }
        packageRepository.findByIdAndOwnerId(environmentId, owner.getId())
                .ifPresent(environment ->
                        settingsService.set(owner, SettingKey.ACTIVE_ENVIRONMENT_ID, environment.getId().toString()));
    }

    @Transactional(readOnly = true)
    public Optional<Long> activePackageId(User owner) {
        String value = settingsService.getSettings(owner).get(SettingKey.ACTIVE_ENVIRONMENT_ID);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public EnvironmentContext contextForActive(User owner) {
        return activePackageId(owner)
                .flatMap(id -> packageRepository.findByIdAndOwnerId(id, owner.getId()))
                .map(environment -> buildContext(owner, environment))
                .orElseGet(() -> buildContext(owner, null));
    }

    @Transactional(readOnly = true)
    public EnvironmentContext contextForRequest(User owner,
                                                String queryString,
                                                Map<String, String> headers) {
        String requested = requestOverrideValue(queryString, headers);
        if (requested == null || requested.isBlank()) {
            return contextForActive(owner);
        }
        String value = requested.trim();
        if ("globals".equalsIgnoreCase(value) || "none".equalsIgnoreCase(value)) {
            return buildContext(owner, null);
        }
        return resolvePackageByValue(owner, value)
                .map(environment -> buildContext(owner, environment))
                .orElseGet(() -> contextForActive(owner));
    }

    @Transactional(readOnly = true)
    public byte[] exportGlobals(User owner) throws IOException {
        EnvironmentExportDto dto = EnvironmentExportDto.builder()
                .version(1)
                .type("globals")
                .exportedAt(Instant.now().toString())
                .exportedBy(owner.getUsername())
                .globals(toVariableDtos(variableRepository.findGlobalsByOwnerId(owner.getId())))
                .build();
        return objectMapper.writeValueAsBytes(dto);
    }

    @Transactional(readOnly = true)
    public Optional<byte[]> exportPackage(Long id, User owner) throws IOException {
        return packageRepository.findByIdAndOwnerId(id, owner.getId())
                .map(environment -> {
                    try {
                        EnvironmentExportDto dto = EnvironmentExportDto.builder()
                                .version(1)
                                .type("environment-package")
                                .exportedAt(Instant.now().toString())
                                .exportedBy(owner.getUsername())
                                .environment(EnvironmentExportDto.EnvironmentPackageDto.builder()
                                        .name(environment.getName())
                                        .description(environment.getDescription())
                                        .variables(toVariableDtos(variableRepository.findByOwnerIdAndPackageId(
                                                owner.getId(), environment.getId())))
                                        .build())
                                .build();
                        return objectMapper.writeValueAsBytes(dto);
                    } catch (IOException e) {
                        throw new IllegalStateException("Environment export failed", e);
                    }
                });
    }

    @Transactional
    public ImportResult importGlobals(byte[] data, User owner, String strategy) throws IOException {
        EnvironmentExportDto dto = objectMapper.readValue(data, EnvironmentExportDto.class);
        List<EnvironmentExportDto.VariableDto> variables = dto.getGlobals();
        if (variables == null || variables.isEmpty()) {
            throw new IllegalArgumentException("This file does not contain globals.");
        }

        List<VariableInput> imported = variables.stream()
                .map(this::toInput)
                .toList();

        if ("replace".equalsIgnoreCase(strategy)) {
            saveGlobals(owner, imported);
            return new ImportResult(imported.size(), 0);
        }

        List<VariableInput> merged = new ArrayList<>();
        variableRepository.findGlobalsByOwnerId(owner.getId()).forEach(variable ->
                merged.add(new VariableInput(variable.getKey(), variable.getValue(),
                        variable.getDescription(), variable.isHidden())));
        mergeVariables(merged, imported);
        saveGlobals(owner, merged);
        return new ImportResult(imported.size(), 0);
    }

    @Transactional
    public ImportResult importPackage(byte[] data,
                                      User owner,
                                      String strategy) throws IOException {
        EnvironmentExportDto dto = objectMapper.readValue(data, EnvironmentExportDto.class);
        EnvironmentExportDto.EnvironmentPackageDto packageDto = dto.getEnvironment();
        if (packageDto == null) {
            throw new IllegalArgumentException("This file does not contain an environment package.");
        }

        String normalizedStrategy = strategy != null ? strategy : "copy";
        List<VariableInput> importedVariables = packageDto.getVariables() != null
                ? packageDto.getVariables().stream().map(this::toInput).toList()
                : List.of();

        EnvironmentPackage environment;
        if ("merge".equalsIgnoreCase(normalizedStrategy) || "replace".equalsIgnoreCase(normalizedStrategy)) {
            String packageName = normalizeName(packageDto.getName());
            environment = packageRepository
                    .findByOwnerIdAndNameIgnoreCase(owner.getId(), packageName)
                    .orElseGet(() -> packageRepository.save(new EnvironmentPackage(
                            owner,
                            uniquePackageName(owner.getId(), packageName, null),
                            packageDto.getDescription())));
        } else {
            environment = packageRepository.save(new EnvironmentPackage(
                    owner,
                    uniquePackageName(owner.getId(), normalizeName(packageDto.getName()), null),
                    packageDto.getDescription()));
        }

        if ("merge".equalsIgnoreCase(normalizedStrategy)) {
            List<VariableInput> merged = new ArrayList<>();
            variableRepository.findByOwnerIdAndPackageId(owner.getId(), environment.getId()).forEach(variable ->
                    merged.add(new VariableInput(variable.getKey(), variable.getValue(),
                            variable.getDescription(), variable.isHidden())));
            mergeVariables(merged, importedVariables);
            savePackageVariables(environment.getId(), owner, merged);
        } else {
            environment.setDescription(packageDto.getDescription());
            packageRepository.save(environment);
            savePackageVariables(environment.getId(), owner, importedVariables);
        }

        return new ImportResult(importedVariables.size(), 1);
    }

    private EnvironmentContext buildContext(User owner, EnvironmentPackage environment) {
        List<EnvironmentVariable> globals = variableRepository.findGlobalsByOwnerId(owner.getId());
        Map<String, String> rawGlobals = toRawMap(globals);
        Map<String, String> resolvedGlobals = templateService.resolveGlobals(rawGlobals);
        if (environment == null) {
            return new EnvironmentContext(null, null, resolvedGlobals, Map.of());
        }

        List<EnvironmentVariable> variables =
                variableRepository.findByOwnerIdAndPackageId(owner.getId(), environment.getId());
        Map<String, String> resolvedVariables = templateService.resolveVariables(toRawMap(variables), rawGlobals);
        return new EnvironmentContext(environment.getId(), environment.getName(), resolvedGlobals, resolvedVariables);
    }

    private String requestOverrideValue(String queryString, Map<String, String> headers) {
        String requested = headerValue(headers, REQUEST_HEADER);
        if (requested == null || requested.isBlank()) {
            requested = parseQueryString(queryString).get(REQUEST_QUERY_PARAM);
        }
        return requested;
    }

    private Optional<EnvironmentPackage> resolvePackageByValue(User owner, String value) {
        try {
            return packageRepository.findByIdAndOwnerId(Long.parseLong(value), owner.getId());
        } catch (NumberFormatException ignored) {
            return packageRepository.findByOwnerIdAndNameIgnoreCase(owner.getId(), value);
        }
    }

    private String headerValue(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private Map<String, String> parseQueryString(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : queryString.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? pair.substring(0, idx) : pair;
            String value = idx >= 0 ? pair.substring(idx + 1) : "";
            result.put(urlDecode(key), urlDecode(value));
        }
        return result;
    }

    private String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private EnvironmentPackageView toPackageView(EnvironmentPackage environment, Long activeId) {
        EnvironmentContext context = buildContext(environment.getOwner(), environment);
        List<EnvironmentVariable> variables = variableRepository.findByOwnerIdAndPackageId(
                environment.getOwner().getId(), environment.getId());
        List<EnvironmentVariableView> variableViews = variables.stream()
                .map(variable -> toVariableView(variable, context.resolveEnvironment(variable.getKey())))
                .toList();
        return new EnvironmentPackageView(
                environment.getId(),
                environment.getName(),
                environment.getDescription(),
                activeId != null && activeId.equals(environment.getId()),
                environment.getUpdatedAt(),
                variableViews);
    }

    private EnvironmentVariableView toVariableView(EnvironmentVariable variable, String resolvedValue) {
        return new EnvironmentVariableView(
                variable.getKey(),
                variable.getValue(),
                resolvedValue != null ? resolvedValue : "",
                variable.getDescription(),
                variable.isHidden());
    }

    private TemplateSuggestion toSuggestion(String scope, String key, String preview, boolean hidden) {
        String expression = "{{" + scope + "." + key + "}}";
        return new TemplateSuggestion(expression, scope, key, hidden ? "********" : preview, hidden);
    }

    private List<EnvironmentVariable> toEntities(User owner,
                                                 EnvironmentPackage environment,
                                                 List<VariableInput> inputs) {
        List<EnvironmentVariable> entities = new ArrayList<>();
        int index = 0;
        for (VariableInput input : sanitize(inputs)) {
            entities.add(new EnvironmentVariable(
                    owner,
                    environment,
                    input.key(),
                    input.value(),
                    input.description(),
                    input.hidden(),
                    index++));
        }
        return entities;
    }

    private List<VariableInput> sanitize(List<VariableInput> inputs) {
        if (inputs == null) {
            return List.of();
        }
        Map<String, VariableInput> result = new LinkedHashMap<>();
        for (VariableInput input : inputs) {
            if (input == null || input.key() == null || input.key().isBlank()) {
                continue;
            }
            String key = input.key().trim();
            result.put(key, new VariableInput(
                    key,
                    input.value() != null ? input.value() : "",
                    input.description() != null ? input.description() : "",
                    input.hidden()));
        }
        return new ArrayList<>(result.values());
    }

    private void mergeVariables(List<VariableInput> target, List<VariableInput> imported) {
        Map<String, VariableInput> byKey = target.stream()
                .collect(Collectors.toMap(
                        input -> input.key().toLowerCase(Locale.ROOT),
                        input -> input,
                        (left, right) -> right,
                        LinkedHashMap::new));
        for (VariableInput input : sanitize(imported)) {
            byKey.put(input.key().toLowerCase(Locale.ROOT), input);
        }
        target.clear();
        target.addAll(byKey.values());
    }

    private EnvironmentVariable copyVariable(EnvironmentVariable source,
                                             User owner,
                                             EnvironmentPackage environment) {
        return new EnvironmentVariable(
                owner,
                environment,
                source.getKey(),
                source.getValue(),
                source.getDescription(),
                source.isHidden(),
                source.getSortOrder());
    }

    private Map<String, String> toRawMap(List<EnvironmentVariable> variables) {
        Map<String, String> result = new LinkedHashMap<>();
        for (EnvironmentVariable variable : variables) {
            result.put(variable.getKey(), variable.getValue() != null ? variable.getValue() : "");
        }
        return result;
    }

    private List<EnvironmentExportDto.VariableDto> toVariableDtos(List<EnvironmentVariable> variables) {
        return variables.stream()
                .map(variable -> EnvironmentExportDto.VariableDto.builder()
                        .key(variable.getKey())
                        .value(variable.getValue())
                        .description(variable.getDescription())
                        .hidden(variable.isHidden())
                        .build())
                .toList();
    }

    private VariableInput toInput(EnvironmentExportDto.VariableDto dto) {
        return new VariableInput(dto.getKey(), dto.getValue(), dto.getDescription(), dto.isHidden());
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return "Environment";
        }
        return name.trim();
    }

    private String uniquePackageName(Long ownerId, String desiredName, Long excludeId) {
        String base = normalizeName(desiredName);
        String candidate = fitName(base, "");
        int counter = 2;
        while (packageRepository.existsNameForOwner(ownerId, candidate, excludeId)) {
            String suffix = " (" + counter++ + ")";
            candidate = fitName(base, suffix);
        }
        return candidate;
    }

    private String fitName(String base, String suffix) {
        int maxBaseLength = MAX_PACKAGE_NAME_LENGTH - suffix.length();
        String trimmed = base.length() > maxBaseLength ? base.substring(0, maxBaseLength).trim() : base;
        return trimmed + suffix;
    }

    public String filenameForPackage(EnvironmentPackageView environment) {
        String slug = slugify(environment.name());
        if (slug.isBlank()) {
            slug = "environment-" + environment.id();
        }
        return slug + "-environment.json";
    }

    public String globalsFilename() {
        return "globals-environment.json";
    }

    private String slugify(String name) {
        return normalizeName(name).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    public record EnvironmentPackageView(Long id,
                                         String name,
                                         String description,
                                         boolean active,
                                         Instant updatedAt,
                                         List<EnvironmentVariableView> variables) {
        public int variableCount() {
            return variables.size();
        }
    }

    public record EnvironmentVariableView(String key,
                                          String value,
                                          String resolvedValue,
                                          String description,
                                          boolean hidden) {
    }

    public record VariableInput(String key,
                                String value,
                                String description,
                                boolean hidden) {
    }

    public record ImportResult(int variables, int packages) {
    }

    public record TemplateSuggestion(String expression,
                                     String scope,
                                     String key,
                                     String preview,
                                     boolean hidden) {
    }
}
