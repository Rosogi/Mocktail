package com.rosogisoft.web;

import com.rosogisoft.config.DeploymentMode;
import com.rosogisoft.config.MocktailProperties;
import com.rosogisoft.domain.User;
import com.rosogisoft.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUserHelper {

    private final UserService userService;
    private final MocktailProperties mocktailProperties;

    public User currentUser () {
        if (mocktailProperties.mode() == DeploymentMode.STANDALONE) {
            return userService.ensureStandaloneUser();
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in security context");
        }

        String provider = mocktailProperties.mode() == DeploymentMode.DATABASE
                ? UserService.AUTH_DATABASE
                : UserService.AUTH_LDAP;

        return userService.findByIdentity(provider, auth.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user '%s' not found in DB".formatted(auth.getName())));
    }
}
