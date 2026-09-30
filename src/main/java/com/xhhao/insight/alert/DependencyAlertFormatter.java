package com.xhhao.insight.alert;

import com.xhhao.insight.halo.DependencyProvider;
import com.xhhao.insight.halo.DependencyStatus;
import java.util.List;
import java.util.stream.Collectors;

/** Formats dependency evidence for alert history and every notification channel. */
final class DependencyAlertFormatter {

    private static final int MAX_PROVIDERS_PER_DEPENDENCY = 3;

    private DependencyAlertFormatter() {}

    static String activeSummary(List<DependencyStatus> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return "Halo 依赖服务异常";
        }
        return "Halo 依赖服务异常：" + dependencies.stream()
            .map(DependencyAlertFormatter::format)
            .collect(Collectors.joining("；"));
    }

    private static String format(DependencyStatus dependency) {
        StringBuilder summary = new StringBuilder(dependency.name());
        if (dependency.message() != null && !dependency.message().isBlank()) {
            summary.append("：").append(dependency.message());
        }
        if (!dependency.providers().isEmpty()) {
            summary.append("（关联插件：");
            summary.append(dependency.providers().stream()
                .limit(MAX_PROVIDERS_PER_DEPENDENCY)
                .map(DependencyAlertFormatter::providerName)
                .collect(Collectors.joining("、")));
            if (dependency.providers().size() > MAX_PROVIDERS_PER_DEPENDENCY) {
                summary.append("等 ").append(dependency.providers().size()).append(" 个");
            }
            summary.append("）");
        }
        return summary.toString();
    }

    private static String providerName(DependencyProvider provider) {
        if (provider.displayName() == null || provider.displayName().isBlank()
            || provider.displayName().equals(provider.pluginId())) {
            return provider.pluginId();
        }
        return provider.displayName() + "（" + provider.pluginId() + "）";
    }
}
