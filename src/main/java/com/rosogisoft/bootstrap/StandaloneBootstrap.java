package com.rosogisoft.bootstrap;

import com.rosogisoft.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mocktail.deployment", name = "mode", havingValue = "standalone")
public class StandaloneBootstrap implements ApplicationRunner {

    private final UserService userService;

    @Override
    public void run(ApplicationArguments args) {
        userService.ensureStandaloneUser();
    }
}
