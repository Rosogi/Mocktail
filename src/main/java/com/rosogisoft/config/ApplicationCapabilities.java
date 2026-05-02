package com.rosogisoft.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("capabilities")
@RequiredArgsConstructor
public class ApplicationCapabilities {

    private final MocktailProperties properties;

    public boolean isShared() {
        return properties.mode() != DeploymentMode.STANDALONE;
    }

    public boolean isAdmin() {
        return properties.mode() == DeploymentMode.DATABASE;
    }

    public boolean isAuthenticationRequired() {
        return properties.mode() != DeploymentMode.STANDALONE;
    }

    public DeploymentMode getMode() {
        return properties.mode();
    }
}
