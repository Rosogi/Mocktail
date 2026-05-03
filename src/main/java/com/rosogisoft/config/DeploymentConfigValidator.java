package com.rosogisoft.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeploymentConfigValidator implements ApplicationRunner, Ordered {

    private final MocktailProperties properties;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (!(environment instanceof ConfigurableEnvironment configurableEnvironment)) {
            return;
        }

        boolean hasExplicitDatabaseAuth = hasExplicitProperty(configurableEnvironment,
                "mocktail.auth.database.", "mocktail.database.bootstrap.admin.", "mocktail.bootstrap.admin.");
        boolean hasExplicitLdapAuth = hasExplicitProperty(configurableEnvironment,
                "mocktail.auth.ldap.", "mocktail.ldap.");

        if (properties.mode() == DeploymentMode.LDAP && hasExplicitDatabaseAuth) {
            throw new IllegalStateException("Database auth settings are not allowed in LDAP deployment mode.");
        }
        if (properties.mode() == DeploymentMode.DATABASE && hasExplicitLdapAuth) {
            throw new IllegalStateException("LDAP auth settings are not allowed in database deployment mode.");
        }
        if (properties.mode() == DeploymentMode.STANDALONE && (hasExplicitDatabaseAuth || hasExplicitLdapAuth)) {
            throw new IllegalStateException("Auth settings are not allowed in standalone deployment mode.");
        }
    }

    private boolean hasExplicitProperty(ConfigurableEnvironment env, String... prefixes) {
        for (PropertySource<?> source : env.getPropertySources()) {
            if (!isExternalSource(source) || !(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                String normalized = normalize(name);
                for (String prefix : prefixes) {
                    if (normalized.startsWith(prefix)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String normalize(String name) {
        String normalized = name.toLowerCase()
                .replace('_', '.')
                .replace('-', '.');
        while (normalized.contains("..")) {
            normalized = normalized.replace("..", ".");
        }
        if (normalized.startsWith("mocktail.ldap.")) {
            return normalized;
        }
        if (normalized.startsWith("mocktail.database.bootstrap.admin.") ||
                normalized.startsWith("mocktail.bootstrap.admin.")) {
            return normalized;
        }
        if (normalized.startsWith("mocktail.auth.")) {
            return normalized;
        }
        if (normalized.startsWith("mocktail.ldap")) {
            return normalized;
        }
        if (normalized.startsWith("mocktail.database")) {
            return normalized;
        }
        return normalized;
    }

    private boolean isExternalSource(PropertySource<?> source) {
        String name = source.getName();
        return name.contains("systemEnvironment") ||
                name.contains("systemProperties") ||
                name.startsWith("commandLineArgs");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
