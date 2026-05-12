package com.rosogisoft.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MockTemplateEngineTest {

    private final TemplateExpressionParser parser = new TemplateExpressionParser();
    private final MockTemplateEngine engine =
            new MockTemplateEngine(new EnvironmentTemplateService(parser), parser);

    @Test
    void usesFallbackForMissingRequestJsonPath() {
        String rendered = engine.render(
                "{\"city\":\"{{address.city ?? \"Unknown\"}}\",\"port\":{{env.port ?? 8080}}}",
                "POST",
                "/api/users",
                null,
                Map.of(),
                "{\"name\":\"Alice\"}",
                EnvironmentContext.empty());

        assertThat(rendered).isEqualTo("{\"city\":\"Unknown\",\"port\":8080}");
    }

    @Test
    void doesNotTreatPlainQuestionMarksAsFallback() {
        String rendered = engine.render(
                "{\"message\":\"What???\"}",
                "GET",
                "/api",
                null,
                Map.of(),
                null,
                EnvironmentContext.empty());

        assertThat(rendered).isEqualTo("{\"message\":\"What???\"}");
    }
}
