package com.rosogisoft.service;

import com.rosogisoft.domain.MockDefinition;
import com.rosogisoft.web.dto.MockDefinitionForm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MockFunctionReferenceService {

    private static final Pattern FUNCTION_CALL = Pattern.compile("fn\\.([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

    private final TemplateExpressionParser expressionParser;
    private final MockFunctionService functionService;

    public boolean hasFunctionReferencesInMatchingFields(MockDefinitionForm form) {
        return !functionNames(form.getHttpMethod()).isEmpty()
                || !functionNames(form.getPathPattern()).isEmpty()
                || !functionNames(form.getRequestBodyContains()).isEmpty()
                || !functionNames(form.getRequestMatchGroups()).isEmpty();
    }

    public boolean usesUserFunctions(MockDefinition mock) {
        return containsUserFunction(mock.getResponseContentType())
                || containsUserFunction(mock.getResponseBody())
                || containsUserFunction(mock.getHttpMethod())
                || containsUserFunction(mock.getPathPattern())
                || containsUserFunction(mock.getRequestBodyContains())
                || containsUserFunction(mock.getRequestMatchGroups())
                || containsUserFunction(mock.getResponseHeaders());
    }

    public Set<String> functionNames(MockDefinition mock) {
        Set<String> names = new LinkedHashSet<>();
        addAll(names, mock.getResponseContentType());
        addAll(names, mock.getResponseBody());
        addAll(names, mock.getHttpMethod());
        addAll(names, mock.getPathPattern());
        addAll(names, mock.getRequestBodyContains());
        addAll(names, mock.getRequestMatchGroups());
        if (mock.getResponseHeaders() != null) {
            mock.getResponseHeaders().forEach((key, value) -> {
                addAll(names, key);
                addAll(names, value);
            });
        }
        return names;
    }

    public Set<String> functionNames(String template) {
        Set<String> names = new LinkedHashSet<>();
        addAll(names, template);
        return names;
    }

    private boolean containsUserFunction(Map<String, String> values) {
        if (values == null) {
            return false;
        }
        return values.entrySet().stream()
                .anyMatch(entry -> containsUserFunction(entry.getKey()) || containsUserFunction(entry.getValue()));
    }

    private boolean containsUserFunction(String template) {
        return functionNames(template).stream().anyMatch(name -> !functionService.isStandardFunction(name));
    }

    private void addAll(Set<String> names, String template) {
        if (template == null || !template.contains("fn.")) {
            return;
        }
        for (TemplateExpressionParser.TemplatePlaceholder placeholder : expressionParser.placeholders(template)) {
            Matcher matcher = FUNCTION_CALL.matcher(placeholder.expression());
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
    }
}
