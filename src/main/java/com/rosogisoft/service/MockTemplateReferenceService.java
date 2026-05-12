package com.rosogisoft.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rosogisoft.domain.MockDefinition;
import com.rosogisoft.domain.User;
import com.rosogisoft.web.dto.MockDefinitionForm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MockTemplateReferenceService {

    private final TemplateExpressionParser expressionParser;
    private final EnvironmentService environmentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public Map<Long, WarningSummary> analyzeAll(List<MockDefinition> mocks, User owner) {
        EnvironmentContext context = environmentService.contextForActive(owner);
        Map<Long, WarningSummary> result = new LinkedHashMap<>();
        for (MockDefinition mock : mocks) {
            result.put(mock.getId(), analyzeSources(sources(mock), context));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public WarningSummary analyze(MockDefinition mock, User owner) {
        return analyzeSources(sources(mock), environmentService.contextForActive(owner));
    }

    @Transactional(readOnly = true)
    public WarningSummary analyze(MockDefinitionForm form, User owner) {
        return analyzeSources(sources(form), environmentService.contextForActive(owner));
    }

    WarningSummary analyze(MockDefinitionForm form, EnvironmentContext context) {
        return analyzeSources(sources(form), context);
    }

    private WarningSummary analyzeSources(List<TemplateSource> sources, EnvironmentContext context) {
        List<MissingReference> missing = new ArrayList<>();
        for (TemplateSource source : sources) {
            for (TemplateExpressionParser.TemplatePlaceholder placeholder :
                    expressionParser.placeholders(source.value())) {
                TemplateExpressionParser.TemplateExpression expression = placeholder.parsed();
                String lookup = expression.lookup();
                String normalized = lookup.toLowerCase(Locale.ROOT);
                if (expression.hasFallback()) {
                    continue;
                }
                if (normalized.startsWith("env.")) {
                    String key = lookup.substring(4);
                    if (!context.variables().containsKey(key) && !context.globals().containsKey(key)) {
                        missing.add(new MissingReference("env", key, source.location()));
                    }
                } else if (normalized.startsWith("global.")) {
                    String key = lookup.substring(7);
                    if (!context.globals().containsKey(key)) {
                        missing.add(new MissingReference("global", key, source.location()));
                    }
                }
            }
        }
        return new WarningSummary(compact(missing));
    }

    private List<MissingReference> compact(List<MissingReference> references) {
        Map<String, MissingReference> compact = new LinkedHashMap<>();
        for (MissingReference reference : references) {
            String key = reference.scope() + "." + reference.key() + "@" + reference.location();
            compact.putIfAbsent(key, reference);
        }
        return new ArrayList<>(compact.values());
    }

    private List<TemplateSource> sources(MockDefinition mock) {
        List<TemplateSource> result = new ArrayList<>();
        addCommonSources(result,
                mock.getHttpMethod(),
                mock.getPathPattern(),
                mock.getRequestBodyContains(),
                mock.getRequestMatchGroups(),
                mock.getResponseContentType(),
                mock.getResponseBody(),
                mock.getResponseHeaders());
        return result;
    }

    private List<TemplateSource> sources(MockDefinitionForm form) {
        List<TemplateSource> result = new ArrayList<>();
        addCommonSources(result,
                form.getHttpMethod(),
                form.getPathPattern(),
                form.getRequestBodyContains(),
                form.getRequestMatchGroups(),
                form.getResponseContentType(),
                form.getResponseBody(),
                form.getParsedHeaders());
        return result;
    }

    private void addCommonSources(List<TemplateSource> result,
                                  String method,
                                  String pathPattern,
                                  String requestBodyContains,
                                  String requestMatchGroups,
                                  String responseContentType,
                                  String responseBody,
                                  Map<String, String> responseHeaders) {
        result.add(new TemplateSource("Method", method));
        result.add(new TemplateSource("Path pattern", pathPattern));
        result.add(new TemplateSource("Basic request matching", requestBodyContains));
        result.add(new TemplateSource("Response content type", responseContentType));
        result.add(new TemplateSource("Response body", responseBody));

        if (responseHeaders != null) {
            responseHeaders.forEach((name, value) -> {
                result.add(new TemplateSource("Extra response header name", name));
                result.add(new TemplateSource("Extra response header value", value));
            });
        }

        readConditionGroups(requestMatchGroups).forEach((groupIndex, group) -> {
            for (int conditionIndex = 0; conditionIndex < group.conditions().size(); conditionIndex++) {
                Condition condition = group.conditions().get(conditionIndex);
                String label = "Advanced request matching " + (groupIndex + 1) + "." + (conditionIndex + 1);
                result.add(new TemplateSource(label + " target", condition.target()));
                result.add(new TemplateSource(label + " value", condition.value()));
            }
        });
    }

    private Map<Integer, ConditionGroup> readConditionGroups(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            List<ConditionGroup> groups = objectMapper.readValue(raw, new TypeReference<>() {});
            Map<Integer, ConditionGroup> result = new LinkedHashMap<>();
            for (int i = 0; i < groups.size(); i++) {
                ConditionGroup group = groups.get(i);
                if (group.conditions() != null) {
                    result.put(i, group);
                }
            }
            return result;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private record TemplateSource(String location, String value) {
    }

    private record ConditionGroup(String connector, List<Condition> conditions) {
    }

    private record Condition(String connector,
                             String source,
                             String xmlMode,
                             String target,
                             String operator,
                             String value,
                             String whitespace) {
    }

    public record MissingReference(String scope,
                                   String key,
                                   String location) {
        public String expression() {
            return scope + "." + key;
        }
    }

    public record WarningSummary(List<MissingReference> missingReferences) {
        public boolean hasWarnings() {
            return missingReferences != null && !missingReferences.isEmpty();
        }

        public int count() {
            return missingReferences != null ? missingReferences.size() : 0;
        }

        public String tooltip() {
            if (!hasWarnings()) {
                return "";
            }
            StringBuilder result = new StringBuilder();
            for (MissingReference reference : missingReferences) {
                if (!result.isEmpty()) {
                    result.append('\n');
                }
                result.append(reference.expression()).append(" — ").append(reference.location());
            }
            return result.toString();
        }
    }
}
