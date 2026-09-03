package com.xhhao.insight.halo;

public record DependencyStatus(String key, String name, String status, String message) {

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
}
