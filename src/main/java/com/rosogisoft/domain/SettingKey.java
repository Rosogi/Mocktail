package com.rosogisoft.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SettingKey {

    DEFAULT_RESPONSE_STATUS ("404", SettingType.INTEGER),
    DEFAULT_RESPONSE_BODY ("{\"error\":\"No mock matched\",\"path\":\"{{request.path}}\"}", SettingType.TEXT),
    DEFAULT_RESPONSE_CT("application/json", SettingType.STRING),
    LANGUAGE("en", SettingType.STRING),
    LOG_PAGE_SIZE("200", SettingType.INTEGER),
    THEME("light", SettingType.STRING),
    ACTIVE_ENVIRONMENT_ID("", SettingType.STRING)
    ;

    private final String defaultValue;
    private final SettingType type;

    public enum SettingType {STRING, INTEGER, BOOLEAN, TEXT}
}
