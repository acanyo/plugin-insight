package com.xhhao.insight.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.SettingFetcher;

@Slf4j
@Component
public class InsightSettingsService {

    private final SettingFetcher settingFetcher;

    public InsightSettingsService(SettingFetcher settingFetcher) {
        this.settingFetcher = settingFetcher;
    }

    public CollectionSettings collection() {
        return fetch("collection", CollectionSettings.class, CollectionSettings::new);
    }

    public AlertSettings alerts() {
        return fetch("alerts", AlertSettings.class, AlertSettings::new);
    }

    public NotificationSettings notifications() {
        return fetch("notifications", NotificationSettings.class, NotificationSettings::new);
    }

    private <T> T fetch(String group, Class<T> type, java.util.function.Supplier<T> defaults) {
        try {
            return settingFetcher.fetch(group, type).orElseGet(defaults);
        } catch (RuntimeException error) {
            log.warn("Failed to read Insight setting group {}, using defaults", group, error);
            return defaults.get();
        }
    }
}
