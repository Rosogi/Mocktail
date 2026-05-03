package com.rosogisoft.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

@Component("appVersion")
public class AppVersion {

    private final ObjectProvider<BuildProperties> buildProperties;

    public AppVersion(ObjectProvider<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties;
    }

    public String getVersion() {
        BuildProperties properties = buildProperties.getIfAvailable();
        if (properties != null && properties.getVersion() != null) {
            return properties.getVersion();
        }
        String implementationVersion = AppVersion.class.getPackage().getImplementationVersion();
        return implementationVersion != null ? implementationVersion : "dev";
    }
}
