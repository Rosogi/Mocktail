package com.rosogisoft.service;

import com.rosogisoft.domain.MockFunction;
import com.rosogisoft.domain.User;
import com.rosogisoft.repository.MockFunctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MockFunctionService {

    private static final int MAX_FUNCTION_NAME_LENGTH = 120;
    private static final Map<String, StandardFunctionSpec> STANDARD_SPECS = standardSpecs();

    private final MockFunctionRepository functionRepository;
    private final StarlarkFunctionExecutor executor;
    private final I18nService i18n;

    public List<FunctionView> standardFunctions() {
        return STANDARD_SPECS.values().stream()
                .map(spec -> new FunctionView(
                        null,
                        spec.name(),
                        description(spec),
                        spec.signatureLabel(),
                        spec.returnType(),
                        source(spec.resourcePath()),
                        true,
                        false,
                        false,
                        true,
                        1,
                        null,
                        false,
                        null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FunctionView> userFunctions(User owner) {
        return functionRepository.findByOwnerId(owner.getId()).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TemplateSuggestion> templateSuggestions(User owner) {
        List<TemplateSuggestion> result = new ArrayList<>();
        standardFunctions().forEach(function ->
                result.add(TemplateSuggestion.function(
                        "{{fn." + function.name() + "()}}",
                        function.signatureLabel(),
                        insertText(function.name()),
                        function.description())));
        if (owner != null) {
            userFunctions(owner).stream()
                    .filter(FunctionView::enabled)
                    .forEach(function -> result.add(TemplateSuggestion.function(
                            "{{fn." + function.name() + "()}}",
                            function.signatureLabel(),
                            insertText(function.name()),
                            function.description())));
        }
        return result;
    }

    private String insertText(String name) {
        return "{{fn." + name + "()}}";
    }

    @Transactional(readOnly = true)
    public Optional<FunctionView> findUserFunction(Long id, User owner) {
        return functionRepository.findByIdAndOwnerId(id, owner.getId()).map(this::toView);
    }

    @Transactional(readOnly = true)
    public Optional<MockFunctionDefinition> resolve(Long ownerId, String name) {
        String normalizedName = normalizeName(name);
        StandardFunctionSpec standard = STANDARD_SPECS.get(normalizedName);
        if (standard != null) {
            return Optional.of(new MockFunctionDefinition(
                    null,
                    null,
                    standard.name(),
                    description(standard),
                    standard.signatureLabel(),
                    standard.returnType(),
                    source(standard.resourcePath()),
                    true,
                    MockFunctionKind.STANDARD));
        }
        if (ownerId == null) {
            return Optional.empty();
        }
        return functionRepository.findByOwnerIdAndName(ownerId, normalizedName)
                .filter(MockFunction::isEnabled)
                .map(this::toDefinition);
    }

    @Transactional(readOnly = true)
    public Object execute(Long ownerId,
                          String name,
                          List<Object> args,
                          TemplateRenderContext context) {
        MockFunctionDefinition definition = resolve(ownerId, name)
                .orElseThrow(() -> new StarlarkFunctionException("Unknown function: fn." + name));
        return executor.execute(definition, args, context);
    }

    public boolean isStandardFunction(String name) {
        return STANDARD_SPECS.containsKey(normalizeName(name));
    }

    @Transactional
    public MockFunction create(FunctionInput input, User owner) {
        validateInput(input, owner, null);
        MockFunction function = new MockFunction(
                owner,
                normalizeName(input.name()),
                clean(input.description()),
                signatureLabel(input),
                cleanReturnType(input.returnType()),
                cleanSource(input.sourceCode()));
        function.setEnabled(input.enabled());
        return functionRepository.save(function);
    }

    @Transactional
    public Optional<MockFunction> update(Long id, FunctionInput input, User owner) {
        return functionRepository.findByIdAndOwnerId(id, owner.getId()).map(function -> {
            ensureEditable(function);
            validateInput(input, owner, id);
            function.setName(normalizeName(input.name()));
            function.setDescription(clean(input.description()));
            function.setSignatureLabel(signatureLabel(input));
            function.setReturnType(cleanReturnType(input.returnType()));
            function.setSourceCode(cleanSource(input.sourceCode()));
            function.setEnabled(input.enabled());
            function.setRevision(function.getRevision() + 1);
            return functionRepository.save(function);
        });
    }

    @Transactional
    public boolean delete(Long id, User owner) {
        return functionRepository.findByIdAndOwnerId(id, owner.getId())
                .map(function -> {
                    ensureEditable(function);
                    return functionRepository.deleteByIdAndOwnerId(id, owner.getId()) > 0;
                })
                .orElse(false);
    }

    @Transactional
    public boolean share(Long id, User owner) {
        return functionRepository.findByIdAndOwnerId(id, owner.getId())
                .map(function -> {
                    ensureEditable(function);
                    function.setShared(true);
                    function.setSharedAt(Instant.now());
                    functionRepository.save(function);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public boolean unshare(Long id, User owner) {
        return functionRepository.findByIdAndOwnerId(id, owner.getId())
                .map(function -> {
                    ensureEditable(function);
                    function.setShared(false);
                    function.setSharedAt(null);
                    functionRepository.save(function);
                    return true;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<SharedFunctionView> sharedFunctions(User viewer) {
        Map<Long, MockFunction> localBySource = new LinkedHashMap<>();
        functionRepository.findByOwnerId(viewer.getId()).forEach(function -> {
            if (function.getSourceFunction() != null) {
                localBySource.put(function.getSourceFunction().getId(), function);
            }
        });
        return functionRepository.findSharedExcludingOwner(viewer.getId()).stream()
                .map(source -> {
                    MockFunction local = localBySource.get(source.getId());
                    return new SharedFunctionView(
                            toView(source),
                            local != null ? toView(local) : null,
                            local != null && source.getRevision() > nullToZero(local.getSourceRevision()));
                })
                .toList();
    }

    @Transactional
    public MockFunction subscribe(Long sourceId, User subscriber) {
        MockFunction source = functionRepository.findSharedById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Shared function not found."));
        if (source.getOwner().getId().equals(subscriber.getId())) {
            throw new IllegalArgumentException("Shared function not found.");
        }
        return functionRepository.findByOwnerIdAndSourceFunctionId(subscriber.getId(), source.getId()).stream()
                .findFirst()
                .orElseGet(() -> copyFunction(source, subscriber, source.getName(), true, source));
    }

    @Transactional
    public MockFunction copyShared(Long sourceId, User owner) {
        MockFunction source = functionRepository.findSharedById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Shared function not found."));
        return copyFunction(source, owner, source.getName() + " copy", false, null);
    }

    @Transactional
    public MockFunction updateSubscription(Long localId, User owner) {
        MockFunction local = functionRepository.findByIdAndOwnerId(localId, owner.getId())
                .orElseThrow(() -> new IllegalArgumentException("Function subscription not found."));
        if (!local.isReadOnly() || local.getSourceFunction() == null) {
            throw new IllegalStateException("Function subscription is invalid.");
        }
        MockFunction source = functionRepository.findById(local.getSourceFunction().getId())
                .orElseThrow(() -> new IllegalStateException("Source function is no longer shared."));
        if (!source.isShared()) {
            throw new IllegalStateException("Source function is no longer shared.");
        }
        local.setName(uniqueName(owner.getId(), source.getName(), local.getId()));
        local.setDescription(source.getDescription());
        local.setSignatureLabel(source.getSignatureLabel());
        local.setReturnType(source.getReturnType());
        local.setSourceCode(source.getSourceCode());
        local.setEnabled(source.isEnabled());
        local.setSourceRevision(source.getRevision());
        return functionRepository.save(local);
    }

    @Transactional
    public boolean unsubscribe(Long localId, User owner) {
        return functionRepository.findByIdAndOwnerId(localId, owner.getId())
                .filter(MockFunction::isReadOnly)
                .map(function -> functionRepository.deleteByIdAndOwnerId(localId, owner.getId()) > 0)
                .orElse(false);
    }

    public StarlarkFunctionExecutor.ValidationResult validateSource(String sourceCode) {
        return executor.validate(sourceCode);
    }

    public TestResult test(FunctionInput input, List<Object> args) {
        StarlarkFunctionExecutor.ValidationResult validation = validateSource(input.sourceCode());
        if (!validation.valid()) {
            return new TestResult(false, null, validation.message());
        }
        MockFunctionDefinition definition = new MockFunctionDefinition(
                null,
                null,
                normalizeName(input.name()),
                clean(input.description()),
                signatureLabel(input),
                cleanReturnType(input.returnType()),
                cleanSource(input.sourceCode()),
                true,
                MockFunctionKind.USER);
        TemplateRenderContext context = new TemplateRenderContext(null, "GET", "/test", null, Map.of(),
                null, EnvironmentContext.empty(), TemplatePhase.PREVIEW);
        try {
            Object value = executor.execute(definition, args, context);
            return new TestResult(true, value, "");
        } catch (RuntimeException e) {
            return new TestResult(false, null, e.getMessage());
        }
    }

    private MockFunction copyFunction(MockFunction source,
                                      User owner,
                                      String preferredName,
                                      boolean readOnly,
                                      MockFunction sourceFunction) {
        MockFunction copy = new MockFunction(
                owner,
                uniqueName(owner.getId(), preferredName, null),
                source.getDescription(),
                source.getSignatureLabel(),
                source.getReturnType(),
                source.getSourceCode());
        copy.setEnabled(source.isEnabled());
        copy.setReadOnly(readOnly);
        copy.setSourceFunction(sourceFunction);
        copy.setSourceRevision(sourceFunction != null ? sourceFunction.getRevision() : null);
        return functionRepository.save(copy);
    }

    private void validateInput(FunctionInput input, User owner, Long excludeId) {
        String name = normalizeName(input.name());
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Function name must start with a letter or underscore and contain only letters, digits, and underscores.");
        }
        if (isStandardFunction(name)) {
            throw new IllegalArgumentException("Function name conflicts with a standard function.");
        }
        if (functionRepository.existsNameForOwner(owner.getId(), name, excludeId)) {
            throw new IllegalArgumentException("Function with this name already exists.");
        }
        StarlarkFunctionExecutor.ValidationResult validation = validateSource(input.sourceCode());
        if (!validation.valid()) {
            throw new IllegalArgumentException(validation.message());
        }
    }

    private void ensureEditable(MockFunction function) {
        if (function.isReadOnly()) {
            throw new IllegalStateException("Subscribed functions are read-only.");
        }
    }

    private int nullToZero(Integer value) {
        return value != null ? value : 0;
    }

    private MockFunctionDefinition toDefinition(MockFunction function) {
        return new MockFunctionDefinition(
                function.getId(),
                function.getOwner().getId(),
                function.getName(),
                function.getDescription(),
                function.getSignatureLabel(),
                function.getReturnType(),
                function.getSourceCode(),
                function.isEnabled(),
                MockFunctionKind.USER);
    }

    private FunctionView toView(MockFunction function) {
        return new FunctionView(
                function.getId(),
                function.getName(),
                function.getDescription(),
                function.getSignatureLabel(),
                function.getReturnType(),
                function.getSourceCode(),
                function.isEnabled(),
                function.isShared(),
                function.isReadOnly(),
                false,
                function.getRevision(),
                function.getSourceRevision(),
                function.getSourceFunction() != null &&
                        function.getSourceFunction().getRevision() > nullToZero(function.getSourceRevision()),
                function.getOwner().getUsername());
    }

    private String signatureLabel(FunctionInput input) {
        String explicit = clean(input.signatureLabel());
        if (!explicit.isBlank()) {
            return explicit;
        }
        return "fn." + normalizeName(input.name()) + "() " + cleanReturnType(input.returnType());
    }

    private String cleanReturnType(String value) {
        String type = clean(value).toLowerCase(Locale.ROOT);
        return type.isBlank() ? "string" : type;
    }

    private String cleanSource(String value) {
        return value != null ? value.strip() : "";
    }

    private String clean(String value) {
        return value != null ? value.trim() : "";
    }

    private String normalizeName(String name) {
        String safe = clean(name);
        if (safe.isBlank()) {
            safe = "userFunction";
        }
        if (safe.length() > MAX_FUNCTION_NAME_LENGTH) {
            safe = safe.substring(0, MAX_FUNCTION_NAME_LENGTH);
        }
        return safe;
    }

    private String uniqueName(Long ownerId, String desiredName, Long excludeId) {
        String base = normalizeName(desiredName).replaceAll("[^A-Za-z0-9_]", "_");
        if (!base.matches("[A-Za-z_].*")) {
            base = "fn_" + base;
        }
        String candidate = base;
        int counter = 2;
        while (isStandardFunction(candidate) || functionRepository.existsNameForOwner(ownerId, candidate, excludeId)) {
            candidate = fitName(base, "_" + counter++);
        }
        return candidate;
    }

    private String fitName(String base, String suffix) {
        int maxBaseLength = MAX_FUNCTION_NAME_LENGTH - suffix.length();
        String trimmed = base.length() > maxBaseLength ? base.substring(0, maxBaseLength) : base;
        return trimmed + suffix;
    }

    private String source(String resourcePath) {
        try {
            return new ClassPathResource(resourcePath).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load standard function source: " + resourcePath, e);
        }
    }

    private String description(StandardFunctionSpec spec) {
        return i18n != null ? i18n.t(spec.descriptionKey()) : spec.descriptionKey();
    }

    private static Map<String, StandardFunctionSpec> standardSpecs() {
        Map<String, StandardFunctionSpec> result = new LinkedHashMap<>();
        add(result, "uuid", "fn.uuid(key string?, format string?) string",
                "string", "functions.standardDescriptions.uuid",
                "functions/standard/uuid.star");
        add(result, "randomInt", "fn.randomInt(min number, max number) number",
                "number", "functions.standardDescriptions.randomInt",
                "functions/standard/randomInt.star");
        add(result, "randomDigits", "fn.randomDigits(length number) string",
                "string", "functions.standardDescriptions.randomDigits",
                "functions/standard/randomDigits.star");
        add(result, "randomAlnum", "fn.randomAlnum(length number) string",
                "string", "functions.standardDescriptions.randomAlnum",
                "functions/standard/randomAlnum.star");
        add(result, "nowEpochMillis", "fn.nowEpochMillis() number",
                "number", "functions.standardDescriptions.nowEpochMillis",
                "functions/standard/nowEpochMillis.star");
        add(result, "sequence", "fn.sequence(name string) number",
                "number", "functions.standardDescriptions.sequence",
                "functions/standard/sequence.star");
        add(result, "ulid", "fn.ulid(key string?) string",
                "string", "functions.standardDescriptions.ulid",
                "functions/standard/ulid.star");
        return result;
    }

    private static void add(Map<String, StandardFunctionSpec> result,
                            String name,
                            String signatureLabel,
                            String returnType,
                            String descriptionKey,
                            String resourcePath) {
        result.put(name, new StandardFunctionSpec(name, signatureLabel, returnType, descriptionKey, resourcePath));
    }

    private record StandardFunctionSpec(String name,
                                        String signatureLabel,
                                        String returnType,
                                        String descriptionKey,
                                        String resourcePath) {
    }

    public record FunctionInput(String name,
                                String description,
                                String signatureLabel,
                                String returnType,
                                String sourceCode,
                                boolean enabled) {
    }

    public record FunctionView(Long id,
                               String name,
                               String description,
                               String signatureLabel,
                               String returnType,
                               String sourceCode,
                               boolean enabled,
                               boolean shared,
                               boolean readOnly,
                               boolean standard,
                               int revision,
                               Integer sourceRevision,
                               boolean updateAvailable,
                               String ownerName) {
    }

    public record SharedFunctionView(FunctionView source,
                                     FunctionView local,
                                     boolean updateAvailable) {
    }

    public record TestResult(boolean success, Object value, String error) {
    }
}
