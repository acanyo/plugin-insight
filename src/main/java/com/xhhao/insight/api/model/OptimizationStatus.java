package com.xhhao.insight.api.model;

import com.xhhao.insight.consumer.PluginConsumerItem;
import com.xhhao.insight.optimization.RuntimeDiagnosis;
import java.util.List;

/** Current runtime diagnosis plus the optional Insight-only overhead profile. */
public record OptimizationStatus(
    String mode,
    boolean enabled,
    boolean canApply,
    long appliedAtMs,
    long updatedAtMs,
    int configuredSamplingSeconds,
    int effectiveSamplingSeconds,
    int optimizedSamplingSeconds,
    int configuredPluginThreadLimit,
    int effectivePluginThreadLimit,
    int optimizedPluginThreadLimit,
    boolean pluginResourceSamplingEnabled,
    RuntimeDiagnosis runtime,
    List<OptimizationChange> changes,
    PluginConsumerItem topConsumer
) {

    public record OptimizationChange(
        String id,
        String title,
        String description,
        boolean automatic,
        boolean applied
    ) {
    }
}
