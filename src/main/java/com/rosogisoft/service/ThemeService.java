package com.rosogisoft.service;

import com.rosogisoft.config.DeploymentMode;
import com.rosogisoft.config.MocktailProperties;
import com.rosogisoft.domain.SettingKey;
import com.rosogisoft.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.List;
import java.util.Locale;

@Service("theme")
@RequiredArgsConstructor
public class ThemeService {

    public static final String LIGHT = "light";
    public static final String DARK = "dark";

    private static final String THEME_REQUEST_ATTRIBUTE = "mocktail.theme";

    private final UserService userService;
    private final UserSettingsService settingsService;
    private final MocktailProperties mocktailProperties;

    public String currentTheme() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            Object cached = attributes.getAttribute(THEME_REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
            if (cached instanceof String theme) {
                return theme;
            }
        }

        String theme = resolveCurrentTheme();
        if (attributes != null) {
            attributes.setAttribute(THEME_REQUEST_ATTRIBUTE, theme, RequestAttributes.SCOPE_REQUEST);
        }
        return theme;
    }

    public String normalizeTheme(String theme) {
        if (theme == null || theme.isBlank()) {
            return LIGHT;
        }
        String normalized = theme.trim().toLowerCase(Locale.ROOT);
        return supportedThemes().contains(normalized) ? normalized : LIGHT;
    }

    public List<String> supportedThemes() {
        return List.of(LIGHT, DARK);
    }

    private String resolveCurrentTheme() {
        if (mocktailProperties.mode() == DeploymentMode.STANDALONE) {
            return themeForUser(userService.ensureStandaloneUser());
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() ||
                auth instanceof AnonymousAuthenticationToken ||
                auth.getName() == null ||
                "anonymousUser".equals(auth.getName())) {
            return LIGHT;
        }

        String provider = mocktailProperties.mode() == DeploymentMode.DATABASE
                ? UserService.AUTH_DATABASE
                : UserService.AUTH_LDAP;
        return userService.findByIdentity(provider, auth.getName())
                .map(this::themeForUser)
                .orElse(LIGHT);
    }

    private String themeForUser(User user) {
        return normalizeTheme(settingsService.getSettings(user).get(SettingKey.THEME));
    }
}
