package com.rosogisoft.service;

import com.rosogisoft.domain.SettingKey;
import com.rosogisoft.domain.User;
import com.rosogisoft.domain.UserSetting;
import com.rosogisoft.repository.UserSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserSettingRepository settingRepository;

    /**
     * Returns a SettingsMap for the user.
     * Missing keys are filled with defaults — no DB write on read.
     */
    @Transactional(readOnly = true)
    public SettingsMap getSettings(User user) {
        List<UserSetting> rows = settingRepository.findAllByOwnerId(user.getId());
        Map<SettingKey, String> map = new EnumMap<>(SettingKey.class);
        for (UserSetting row : rows) {
            map.put(row.getKey(), row.getValue());
        }
        return new SettingsMap(map);
    }

    /** Save a single setting value */
    @Transactional
    public void set(User user, SettingKey key, String value) {
        settingRepository.upsert(user.getId(), key.name(), value);
    }

    /** Save multiple settings at once */
    @Transactional
    public void setAll(User user, Map<SettingKey, String> values) {
        values.forEach((key, value) ->
                settingRepository.upsert(user.getId(), key.name(), value));
    }

    // ── SettingsMap — typed read access with fallback to defaults ──

    public static class SettingsMap {

        private final Map<SettingKey, String> data;

        public SettingsMap(Map<SettingKey, String> data) {
            this.data = data;
        }

        public String get(SettingKey key) {
            return data.getOrDefault(key, key.getDefaultValue());
        }

        public int getInt(SettingKey key) {
            try {
                return Integer.parseInt(get(key));
            } catch (NumberFormatException e) {
                return Integer.parseInt(key.getDefaultValue());
            }
        }

        public boolean getBoolean(SettingKey key) {
            return Boolean.parseBoolean(get(key));
        }

        /** Expose raw map for Thymeleaf model */
        public Map<SettingKey, String> asMap() {
            // Fill in all missing keys with defaults for UI
            Map<SettingKey, String> full = new EnumMap<>(SettingKey.class);
            for (SettingKey key : SettingKey.values()) {
                full.put(key, get(key));
            }
            return full;
        }

        public Map<String, String> asStringMap() {
            Map<String, String> full = new LinkedHashMap<>();

            for (SettingKey key : SettingKey.values()) {
                full.put(key.name(), get(key));
            }

            return full;
        }
    }
}
