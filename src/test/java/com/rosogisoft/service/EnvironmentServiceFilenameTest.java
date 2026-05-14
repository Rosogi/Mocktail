package com.rosogisoft.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentServiceFilenameTest {

    private final EnvironmentService service = new EnvironmentService(null, null, null, null);

    @Test
    void usesPackageNameForAsciiExportFilename() {
        var environment = new EnvironmentService.EnvironmentPackageView(
                1L,
                "Local Package 01",
                "",
                false,
                Instant.EPOCH,
                List.of());

        assertThat(service.filenameForPackage(environment)).isEqualTo("local-package-01.json");
    }

    @Test
    void fallsBackToPlaceholderWhenPackageNameContainsUnsupportedLetters() {
        var environment = new EnvironmentService.EnvironmentPackageView(
                2L,
                "Русский пакет",
                "",
                false,
                Instant.EPOCH,
                List.of());

        assertThat(service.filenameForPackage(environment)).isEqualTo("exported_package.json");
    }
}
