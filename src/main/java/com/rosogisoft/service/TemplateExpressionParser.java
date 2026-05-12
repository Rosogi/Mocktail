package com.rosogisoft.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TemplateExpressionParser {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^}]+)}}");

    public List<TemplatePlaceholder> placeholders(String template) {
        if (template == null || !template.contains("{{")) {
            return List.of();
        }
        List<TemplatePlaceholder> result = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            result.add(new TemplatePlaceholder(
                    matcher.group(0),
                    expression,
                    parse(expression),
                    matcher.start(),
                    matcher.end()));
        }
        return result;
    }

    public TemplateExpression parse(String expression) {
        String safeExpression = expression != null ? expression.trim() : "";
        int separator = fallbackSeparator(safeExpression);
        if (separator < 0) {
            return new TemplateExpression(safeExpression, Optional.empty());
        }
        String lookup = safeExpression.substring(0, separator).trim();
        String fallback = safeExpression.substring(separator + 2).trim();
        return new TemplateExpression(lookup, Optional.of(parseLiteral(fallback)));
    }

    private int fallbackSeparator(String expression) {
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < expression.length() - 1; i++) {
            char current = expression.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (quote != 0) {
                if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                continue;
            }
            if (current == '?' && expression.charAt(i + 1) == '?') {
                return i;
            }
        }
        return -1;
    }

    private TemplateLiteral parseLiteral(String raw) {
        if (raw == null || raw.isBlank()) {
            return new TemplateLiteral("", TemplateLiteralType.STRING);
        }
        String value = raw.trim();
        if (isQuoted(value)) {
            return new TemplateLiteral(unquote(value), TemplateLiteralType.STRING);
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return new TemplateLiteral(value.toLowerCase(), TemplateLiteralType.BOOLEAN);
        }
        if ("null".equalsIgnoreCase(value)) {
            return new TemplateLiteral("null", TemplateLiteralType.NULL);
        }
        if (value.matches("-?\\d+(\\.\\d+)?")) {
            return new TemplateLiteral(value, TemplateLiteralType.NUMBER);
        }
        return new TemplateLiteral(value, TemplateLiteralType.STRING);
    }

    private boolean isQuoted(String value) {
        if (value.length() < 2) {
            return false;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        return (first == '\'' || first == '"') && first == last;
    }

    private String unquote(String value) {
        String body = value.substring(1, value.length() - 1);
        StringBuilder result = new StringBuilder(body.length());
        boolean escaped = false;
        for (int i = 0; i < body.length(); i++) {
            char current = body.charAt(i);
            if (escaped) {
                result.append(switch (current) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> current;
                });
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            result.append(current);
        }
        if (escaped) {
            result.append('\\');
        }
        return result.toString();
    }

    public record TemplatePlaceholder(String placeholder,
                                      String expression,
                                      TemplateExpression parsed,
                                      int start,
                                      int end) {
    }

    public record TemplateExpression(String lookup,
                                     Optional<TemplateLiteral> fallback) {
        public boolean hasFallback() {
            return fallback.isPresent();
        }
    }

    public record TemplateLiteral(String value,
                                  TemplateLiteralType type) {
    }

    public enum TemplateLiteralType {
        STRING,
        NUMBER,
        BOOLEAN,
        NULL
    }
}
