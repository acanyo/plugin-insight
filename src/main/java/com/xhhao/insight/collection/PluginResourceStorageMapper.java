package com.xhhao.insight.collection;

import com.xhhao.insight.resource.ResourceAttributionSample;
import com.xhhao.insight.storage.model.PluginResourceMinute;
import com.xhhao.insight.storage.model.ResourceAttributionMinute;
import java.util.List;

/** Converts the live sampling domain into minute-level SQLite projections. */
public final class PluginResourceStorageMapper {

    private PluginResourceStorageMapper() {
    }

    public static List<PluginResourceMinute> toPluginMetrics(ResourceAttributionSample sample) {
        long bucket = minuteBucket(sample.sampledAtMs());
        return sample.plugins().stream().map(plugin -> new PluginResourceMinute(
            bucket, plugin.pluginId(), 1, plugin.cpuMeasurementSamples(),
            plugin.allocationMeasurementSamples(), plugin.cpuActiveSamples(),
            plugin.activeThreadSamples(), plugin.liveThreads(), plugin.cpuTimeNs(),
            plugin.allocatedBytes())).toList();
    }

    public static ResourceAttributionMinute toCoverage(ResourceAttributionSample sample) {
        return new ResourceAttributionMinute(
            minuteBucket(sample.sampledAtMs()), 1, sample.cpuAvailable() ? 1 : 0,
            sample.allocationAvailable() ? 1 : 0, sample.truncated() ? 1 : 0,
            sample.inspectedThreads(), sample.attributedThreads(), sample.totalCpuTimeNs(),
            sample.attributedCpuTimeNs(), sample.totalAllocatedBytes(),
            sample.attributedAllocatedBytes());
    }

    private static long minuteBucket(long timestampMs) {
        return timestampMs / 60_000 * 60_000;
    }
}
