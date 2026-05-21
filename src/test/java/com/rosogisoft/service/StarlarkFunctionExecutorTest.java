package com.rosogisoft.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StarlarkFunctionExecutorTest {

    private final StarlarkFunctionExecutor executor = new StarlarkFunctionExecutor();

    @Test
    void executesSimplePureFunction() {
        MockFunctionDefinition definition = new MockFunctionDefinition(
                1L,
                1L,
                "stripBearer",
                "",
                "fn.stripBearer(authHeader string) string",
                "string",
                """
                        def main(authHeader):
                            if authHeader.startswith("Bearer "):
                                return authHeader.substring(7, None)
                            return authHeader
                        """,
                true,
                MockFunctionKind.USER);

        Object result = executor.execute(definition, List.of("Bearer token-123"), context());

        assertThat(result).isEqualTo("token-123");
    }

    @Test
    void reusesRuntimeUuidWhenKeyIsProvided() {
        MockFunctionDefinition definition = new MockFunctionDefinition(
                null,
                null,
                "uuid",
                "",
                "fn.uuid(key string?) string",
                "string",
                """
                        def main(key = None):
                            return runtime.uuid(key)
                        """,
                true,
                MockFunctionKind.STANDARD);
        TemplateRenderContext context = context();

        Object first = executor.execute(definition, List.of("correlationId"), context);
        Object second = executor.execute(definition, List.of("correlationId"), context);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void acceptsLegacyTypedParameters() {
        MockFunctionDefinition definition = new MockFunctionDefinition(
                1L,
                1L,
                "joinValues",
                "",
                "fn.joinValues(firstValue string, secondValue number) string",
                "string",
                """
                        def main(firstValue string, secondValue number):
                            return "Общее значение" + firstValue + "-" + secondValue
                        """,
                true,
                MockFunctionKind.USER);

        Object result = executor.execute(definition, List.of("222", 333L), context());

        assertThat(result).isEqualTo("Общее значение222-333");
    }

    @Test
    void failsFastForUnknownVariables() {
        MockFunctionDefinition definition = new MockFunctionDefinition(
                1L,
                1L,
                "joinValues",
                "",
                "fn.joinValues(firstValue string) string",
                "string",
                """
                        def main(firstValue):
                            return fisrtValue
                        """,
                true,
                MockFunctionKind.USER);

        assertThatThrownBy(() -> executor.execute(definition, List.of("222"), context()))
                .isInstanceOf(StarlarkFunctionException.class)
                .hasMessageContaining("Unknown variable: fisrtValue. Did you mean firstValue?");
    }

    private TemplateRenderContext context() {
        return new TemplateRenderContext(
                1L,
                "GET",
                "/api",
                null,
                Map.of(),
                null,
                EnvironmentContext.empty(),
                TemplatePhase.RESPONSE);
    }
}
