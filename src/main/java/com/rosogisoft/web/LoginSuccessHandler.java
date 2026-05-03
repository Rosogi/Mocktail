package com.rosogisoft.web;

import com.rosogisoft.config.DeploymentMode;
import com.rosogisoft.config.MocktailProperties;
import com.rosogisoft.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserService userService;
    private final MocktailProperties mocktailProperties;

    public LoginSuccessHandler (UserService userService,
                                MocktailProperties mocktailProperties) {
        this.userService = userService;
        this.mocktailProperties = mocktailProperties;
        setDefaultTargetUrl("/dashboard");
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess (HttpServletRequest request,
                                         HttpServletResponse response,
                                         Authentication authentication)
            throws IOException, ServletException {
        String login = authentication.getName();
        if (mocktailProperties.mode() == DeploymentMode.LDAP) {
            userService.ensureLdapUserExists(login);
        }
        log.info("Пользователь '{}' вошел в систему", login);
        if (mocktailProperties.mode() == DeploymentMode.DATABASE &&
                userService.isPasswordChangeRequired(login)) {
            getRedirectStrategy().sendRedirect(request, response, "/password/change");
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
