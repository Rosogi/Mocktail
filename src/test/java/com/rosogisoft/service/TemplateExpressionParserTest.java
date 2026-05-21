package com.rosogisoft.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateExpressionParserTest {

    private final TemplateExpressionParser parser = new TemplateExpressionParser();

    @Test
    void parsesFallbackWithAnySpacing() {
        assertThat(parser.parse("env.port??8080").fallback().orElseThrow().value()).isEqualTo("8080");
        assertThat(parser.parse("env.port ?? 8080").fallback().orElseThrow().value()).isEqualTo("8080");
        assertThat(parser.parse("env.port      ??      8080").fallback().orElseThrow().value()).isEqualTo("8080");
    }

    @Test
    void ignoresQuestionMarksInsideQuotedFallback() {
        var expression = parser.parse("env.message ?? \"What???\"");

        assertThat(expression.lookup()).isEqualTo("env.message");
        assertThat(expression.fallback().orElseThrow().value()).isEqualTo("What???");
    }

    @Test
    void onlyParsesExpressionsInsidePlaceholders() {
        assertThat(parser.placeholders("{\"message\":\"What???\"}")).isEmpty();
        assertThat(parser.placeholders("{\"message\":\"{{env.message ?? 'What???'}}\"}"))
                .hasSize(1)
                .first()
                .extracting(TemplateExpressionParser.TemplatePlaceholder::expression)
                .isEqualTo("env.message ?? 'What???'");
    }

    @Test
    void parsesNestedFunctionPlaceholdersAsOnePlaceholder() {
        assertThat(parser.placeholders("{{fn.uuid({{env.key}})}}"))
                .hasSize(1)
                .first()
                .extracting(TemplateExpressionParser.TemplatePlaceholder::expression)
                .isEqualTo("fn.uuid({{env.key}})");
    }

    @Test
    void parsesTypedFallbackLiterals() {
        assertThat(parser.parse("env.port ?? 8080").fallback().orElseThrow().type())
                .isEqualTo(TemplateExpressionParser.TemplateLiteralType.NUMBER);
        assertThat(parser.parse("env.enabled ?? false").fallback().orElseThrow().type())
                .isEqualTo(TemplateExpressionParser.TemplateLiteralType.BOOLEAN);
        assertThat(parser.parse("env.deletedAt ?? null").fallback().orElseThrow().type())
                .isEqualTo(TemplateExpressionParser.TemplateLiteralType.NULL);
    }
}
