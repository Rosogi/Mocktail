package com.rosogisoft.service;

public record MockFunctionDefinition(Long id,
                                     Long ownerId,
                                     String name,
                                     String description,
                                     String signatureLabel,
                                     String returnType,
                                     String sourceCode,
                                     boolean enabled,
                                     MockFunctionKind kind) {

    public boolean standard() {
        return kind == MockFunctionKind.STANDARD;
    }
}
