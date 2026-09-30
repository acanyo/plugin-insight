package com.xhhao.insight.halo;

import java.util.List;

public record DependencyStatus(
    String key,
    String name,
    String status,
    String message,
    List<DependencyProvider> providers
) {

    public DependencyStatus {
        providers = providers == null ? List.of() : List.copyOf(providers);
    }

    public DependencyStatus(String key, String name, String status, String message) {
        this(key, name, status, message, List.of());
    }

    public static DependencyStatus healthy(String key, String name, String message) {
        return new DependencyStatus(key, name, "healthy", message);
    }

    public static DependencyStatus warning(String key, String name, String message) {
        return new DependencyStatus(key, name, "warning", message);
    }

    public static DependencyStatus unknown(String key, String name, String message) {
        return new DependencyStatus(key, name, "unknown", message);
    }

    public static DependencyStatus critical(String key, String name, String message) {
        return new DependencyStatus(key, name, "critical", message);
    }

    public DependencyStatus withProviders(List<DependencyProvider> relatedProviders) {
        return new DependencyStatus(key, name, status, message, relatedProviders);
    }
}
