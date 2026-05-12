package com.rosogisoft.service;

import java.util.Map;

public record EnvironmentContext(Long packageId,
                                 String packageName,
                                 Map<String, String> globals,
                                 Map<String, String> variables) {

    public static EnvironmentContext empty() {
        return new EnvironmentContext(null, null, Map.of(), Map.of());
    }

    public String resolveGlobal(String key) {
        return globals.get(key);
    }

    public String resolveEnvironment(String key) {
        String value = variables.get(key);
        return value != null ? value : globals.get(key);
    }
}
