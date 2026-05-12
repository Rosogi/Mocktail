package com.rosogisoft.service;

import com.rosogisoft.web.dto.MockDefinitionForm;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MockTemplateReferenceServiceTest {

    private final MockTemplateReferenceService service = new MockTemplateReferenceService(
            new TemplateExpressionParser(), null);

    @Test
    void warnsOnlyForMissingEnvAndGlobalReferencesWithoutFallback() throws Exception {
        MockDefinitionForm form = new MockDefinitionForm();
        form.setResponseBody("""
                {
                  "ok": "{{env.baseUrl}}",
                  "fallback": "{{env.missing ?? "http://localhost"}}",
                  "request": "{{address.city}}",
                  "company": "{{global.companyId}}"
                }
                """);

        EnvironmentContext context = new EnvironmentContext(
                1L,
                "Local",
                Map.of("companyId", "rosogisoft"),
                Map.of());
        MockTemplateReferenceService.WarningSummary summary = service.analyze(form, context);

        assertThat(summary.missingReferences())
                .extracting(MockTemplateReferenceService.MissingReference::expression)
                .containsExactly("env.baseUrl");
    }
}
