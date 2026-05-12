package com.rosogisoft.service;

import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EnvironmentTemplateService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^}]+)}}");

    private final TemplateExpressionParser expressionParser;

    public EnvironmentTemplateService(TemplateExpressionParser expressionParser) {
        this.expressionParser = expressionParser;
    }

    public String resolve(String template, EnvironmentContext context) {
        return resolve(template, context, false);
    }

    public String resolve(String template, EnvironmentContext context, boolean allowUnqualified) {
        if (template == null || !template.contains("{{")) {
            return template;
        }
        EnvironmentContext safeContext = context != null ? context : EnvironmentContext.empty();
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder(template.length() + 32);
        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            String replacement = resolveExpression(expression, safeContext, allowUnqualified);
            matcher.appendReplacement(result,
                    replacement != null
                            ? Matcher.quoteReplacement(replacement)
                            : matcher.group(0));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public String resolveExpression(String expression,
                                    EnvironmentContext context,
                                    boolean allowUnqualified) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        TemplateExpressionParser.TemplateExpression parsed = expressionParser.parse(expression);
        String resolved = resolveLookup(parsed.lookup(), context, allowUnqualified);
        return resolved != null
                ? resolved
                : parsed.fallback().map(TemplateExpressionParser.TemplateLiteral::value).orElse(null);
    }

    private String resolveLookup(String expression,
                                 EnvironmentContext context,
                                 boolean allowUnqualified) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        EnvironmentContext safeContext = context != null ? context : EnvironmentContext.empty();
        String normalized = expression.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("env.")) {
            return safeContext.resolveEnvironment(expression.substring(4));
        }
        if (normalized.startsWith("global.")) {
            return safeContext.resolveGlobal(expression.substring(7));
        }
        return allowUnqualified ? safeContext.resolveEnvironment(expression) : null;
    }

    public Map<String, String> resolveGlobals(Map<String, String> rawGlobals) {
        Map<String, String> resolved = new java.util.LinkedHashMap<>();
        rawGlobals.forEach((key, value) ->
                resolved.put(key, nullToEmpty(resolveVariable(key, rawGlobals, Map.of(), true, new HashSet<>()))));
        return resolved;
    }

    public Map<String, String> resolveVariables(Map<String, String> rawVariables,
                                                Map<String, String> rawGlobals) {
        Map<String, String> resolvedGlobals = resolveGlobals(rawGlobals);
        Map<String, String> resolved = new java.util.LinkedHashMap<>();
        rawVariables.forEach((key, value) ->
                resolved.put(key, nullToEmpty(resolveVariable(key, rawVariables, resolvedGlobals, false, new HashSet<>()))));
        return resolved;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String resolveVariable(String key,
                                   Map<String, String> rawValues,
                                   Map<String, String> fallbackValues,
                                   boolean globalsOnly,
                                   Set<String> stack) {
        String raw = rawValues.get(key);
        if (raw == null) {
            return fallbackValues.get(key);
        }
        if (!stack.add(key)) {
            return "{{" + key + "}}";
        }
        Matcher matcher = PLACEHOLDER.matcher(raw);
        StringBuilder result = new StringBuilder(raw.length() + 32);
        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            String replacement = resolveVariableExpression(
                    expression, rawValues, fallbackValues, globalsOnly, stack);
            matcher.appendReplacement(result,
                    replacement != null
                            ? Matcher.quoteReplacement(replacement)
                            : matcher.group(0));
        }
        matcher.appendTail(result);
        stack.remove(key);
        return result.toString();
    }

    private String resolveVariableExpression(String expression,
                                             Map<String, String> rawValues,
                                             Map<String, String> fallbackValues,
                                             boolean globalsOnly,
                                             Set<String> stack) {
        TemplateExpressionParser.TemplateExpression parsed = expressionParser.parse(expression);
        String replacement = resolveVariableLookup(
                parsed.lookup(), rawValues, fallbackValues, globalsOnly, stack);
        return replacement != null
                ? replacement
                : parsed.fallback().map(TemplateExpressionParser.TemplateLiteral::value).orElse(null);
    }

    private String resolveVariableLookup(String expression,
                                         Map<String, String> rawValues,
                                         Map<String, String> fallbackValues,
                                         boolean globalsOnly,
                                         Set<String> stack) {
        String normalized = expression.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("global.")) {
            String key = expression.substring(7);
            return globalsOnly
                    ? resolveVariable(key, rawValues, Map.of(), true, stack)
                    : fallbackValues.get(key);
        }
        String key = normalized.startsWith("env.")
                ? expression.substring(4)
                : expression;
        if (!globalsOnly && rawValues.containsKey(key)) {
            return resolveVariable(key, rawValues, fallbackValues, false, stack);
        }
        if (globalsOnly || fallbackValues.containsKey(key)) {
            return globalsOnly
                    ? resolveVariable(key, rawValues, Map.of(), true, stack)
                    : fallbackValues.get(key);
        }
        return null;
    }
}
