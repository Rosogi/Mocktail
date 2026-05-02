package com.rosogisoft.bootstrap;

import com.rosogisoft.config.MocktailProperties;
import com.rosogisoft.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mocktail.deployment", name = "mode", havingValue = "database")
public class DatabaseAuthBootstrap implements ApplicationRunner {

    private final UserService userService;
    private final MocktailProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (userService.hasDatabaseAdmin()) {
            return;
        }

        MocktailProperties.BootstrapAdmin admin = properties.getAuth().getDatabase().getBootstrapAdmin();
        UserService.OneTimePassword created = userService.createBootstrapAdmin(
                admin.getLogin(),
                Optional.ofNullable(admin.getPassword())
        );

        log.warn("============================================================");
        log.warn("Mocktail database auth bootstrap administrator was created.");
        log.warn("Login: {}", created.login());
        log.warn("Temporary password: {}", created.password());
        log.warn("This password is shown once. Change it after the first login.");
        log.warn("============================================================");
    }
}
