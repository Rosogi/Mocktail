package com.rosogisoft.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rosogisoft.domain.SettingKey;
import com.rosogisoft.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service("i18n")
@RequiredArgsConstructor
public class I18nService {

    private static final String DEFAULT_LANGUAGE = "en";
    private static final String LANGUAGE_REQUEST_ATTRIBUTE = "mocktail.language";

    private final UserService userService;
    private final UserSettingsService settingsService;
    private final ObjectMapper objectMapper;

    private Map<String, Map<String, String>> translations = Map.of();

    @PostConstruct
    void loadTranslations() throws IOException {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:i18n/*.json");
        Map<String, Map<String, String>> loaded = new LinkedHashMap<>();

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || !filename.endsWith(".json")) {
                continue;
            }
            String language = filename.substring(0, filename.length() - ".json".length());
            try (InputStream in = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(in);
                Map<String, String> messages = new LinkedHashMap<>();
                flatten("", root, messages);
                loaded.put(language, Map.copyOf(messages));
                log.info("Loaded {} i18n messages for language '{}'", messages.size(), language);
            }
        }

        translations = Map.copyOf(loaded);
        if (!translations.containsKey(DEFAULT_LANGUAGE)) {
            log.warn("Default i18n language '{}' is missing", DEFAULT_LANGUAGE);
        }
    }

    public String t(String key, Object... args) {
        return translate(currentLanguage(), key, args);
    }

    public String tForLanguage(String language, String key, Object... args) {
        return translate(normalizeLanguage(language), key, args);
    }

    public String currentLanguage() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            Object cached = attributes.getAttribute(LANGUAGE_REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
            if (cached instanceof String language) {
                return language;
            }
        }

        String language = resolveCurrentLanguage();
        if (attributes != null) {
            attributes.setAttribute(LANGUAGE_REQUEST_ATTRIBUTE, language, RequestAttributes.SCOPE_REQUEST);
        }
        return language;
    }

    public String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        return translations.containsKey(normalized) ? normalized : DEFAULT_LANGUAGE;
    }

    public List<LanguageOption> supportedLanguages() {
        return List.of(
                new LanguageOption("en", "English"),
                new LanguageOption("ru", "Русский")
        );
    }

    private String translate(String language, String key, Object... args) {
        String pattern = Optional.ofNullable(translations.get(language))
                .map(messages -> messages.get(key))
                .orElseGet(() -> Optional.ofNullable(translations.get(DEFAULT_LANGUAGE))
                        .map(messages -> messages.get(key))
                        .orElse(key));

        if (args == null || args.length == 0) {
            return pattern;
        }
        return new MessageFormat(pattern, Locale.forLanguageTag(language)).format(args);
    }

    private String resolveCurrentLanguage() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() ||
                auth instanceof AnonymousAuthenticationToken ||
                auth.getName() == null ||
                "anonymousUser".equals(auth.getName())) {
            return DEFAULT_LANGUAGE;
        }

        return userService.findByUsername(auth.getName())
                .map(this::languageForUser)
                .orElse(DEFAULT_LANGUAGE);
    }

    private String languageForUser(User user) {
        return normalizeLanguage(settingsService.getSettings(user).get(SettingKey.LANGUAGE));
    }

    private void flatten(String prefix, JsonNode node, Map<String, String> target) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = prefix.isBlank() ? entry.getKey() : prefix + "." + entry.getKey();
                flatten(key, entry.getValue(), target);
            });
            return;
        }
        target.put(prefix, node.asText());
    }

    public record LanguageOption(String code, String label) {}
}
