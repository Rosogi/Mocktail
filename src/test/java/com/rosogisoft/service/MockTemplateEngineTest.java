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

    @Test
    void resolvesFunctionArgumentsFromNestedTemplates() {
        MockFunctionService functionService = new MockFunctionService(null, null, null) {
            @Override
            public Object execute(Long ownerId, String name, java.util.List<Object> args, TemplateRenderContext context) {
                return args.getFirst();
            }
        };
        MockTemplateEngine functionEngine =
                new MockTemplateEngine(new EnvironmentTemplateService(parser), parser, functionService);

        String rendered = functionEngine.render(
                "{{fn.echo({{env.token}})}}",
                "GET",
                "/api",
                null,
                Map.of(),
                null,
                new EnvironmentContext(1L, "Local", Map.of(), Map.of("token", "abc")),
                TemplatePhase.RESPONSE);

        assertThat(rendered).isEqualTo("abc");
    }

    @Test
    void leavesFunctionsUnresolvedDuringMatchingPhase() {
        MockFunctionService functionService = new MockFunctionService(null, null, null) {
            @Override
            public Object execute(Long ownerId, String name, java.util.List<Object> args, TemplateRenderContext context) {
                throw new AssertionError("Functions must not execute during matching.");
            }
        };
        MockTemplateEngine functionEngine =
                new MockTemplateEngine(new EnvironmentTemplateService(parser), parser, functionService);

        String rendered = functionEngine.render(
                "{{fn.uuid()}}",
                "GET",
                "/api",
                null,
                Map.of(),
                null,
                EnvironmentContext.empty(),
                TemplatePhase.MATCHING);

        assertThat(rendered).isEqualTo("{{fn.uuid()}}");
    }
}
