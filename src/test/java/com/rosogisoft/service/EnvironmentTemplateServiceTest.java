package com.rosogisoft.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentTemplateServiceTest {

    private final TemplateExpressionParser parser = new TemplateExpressionParser();
    private final EnvironmentTemplateService service = new EnvironmentTemplateService(parser);

    @Test
    void resolvesGlobalsFromOtherGlobals() {
        Map<String, String> globals = new LinkedHashMap<>();
        globals.put("address", "http://localhost");
        globals.put("port", ":8080");
        globals.put("url", "{{address}}{{port}}");

        assertThat(service.resolveGlobals(globals))
                .containsEntry("url", "http://localhost:8080");
    }

    @Test
    void resolvesPackageValuesWithGlobalFallbacks() {
        Map<String, String> globals = new LinkedHashMap<>();
        globals.put("address", "http://localhost");
        globals.put("port", ":8080");

        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("port", ":9000");
        variables.put("url", "{{address}}{{port}}/api");
        variables.put("globalPort", "{{global.port}}");

        assertThat(service.resolveVariables(variables, globals))
                .containsEntry("url", "http://localhost:9000/api")
                .containsEntry("globalPort", ":8080");
    }

    @Test
    void keepsUnresolvedCyclesVisible() {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("a", "{{b}}");
        variables.put("b", "{{a}}");

        assertThat(service.resolveVariables(variables, Map.of()))
                .containsEntry("a", "{{a}}")
                .containsEntry("b", "{{b}}");
    }

    @Test
    void resolvesRuntimeEnvAndGlobalExpressions() {
        EnvironmentContext context = new EnvironmentContext(
                1L,
                "Local",
                Map.of("companyId", "rosogisoft"),
                Map.of("url", "http://localhost:9000"));

        assertThat(service.resolve("{{env.url}}/{{global.companyId}}/{{missing ?? fallback}}", context))
                .isEqualTo("http://localhost:9000/rosogisoft/fallback");
    }

    @Test
    void resolvesFallbacksWithoutSpacesAndWithLiteralTypes() {
        EnvironmentContext context = EnvironmentContext.empty();

        assertThat(service.resolve("{{env.port??8080}} {{env.enabled ?? true}} {{env.deletedAt ?? null}}", context))
                .isEqualTo("8080 true null");
    }
}
