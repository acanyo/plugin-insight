package com.xhhao.insight.consumer;

import com.xhhao.insight.halo.PluginIdentityResolver;
import com.xhhao.insight.halo.PluginRuntimeInfo;
import com.xhhao.insight.runtime.MonitoringState;
import com.xhhao.insight.storage.model.PluginRequestPersistenceSummary;
import com.xhhao.insight.storage.model.PluginResourcePersistenceSummary;
import com.xhhao.insight.storage.model.ResourceAttributionSummary;
import com.xhhao.insight.storage.repository.PluginMetricRepository;
import com.xhhao.insight.storage.repository.PluginResourceRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Combines minute-level resource and request evidence into a bounded sustained-pressure rank. */
@Component
public class PluginConsumerService {

    private static final int MAX_OBSERVED_IDENTITIES = 2_000;
    private static final int MAX_RESULTS = 20;

    private final PluginResourceRepository resources;
    private final PluginMetricRepository requests;
    private final MonitoringState state;
    private final PluginIdentityResolver identities;

    public PluginConsumerService(
        PluginResourceRepository resources,
        PluginMetricRepository requests,
        MonitoringState state,
        PluginIdentityResolver identities
    ) {
        this.resources = resources;
        this.requests = requests;
        this.state = state;
        this.identities = identities;
    }

    public List<PluginConsumerItem> find(long fromInclusiveMs, long toExclusiveMs) {
        Map<String, PluginRuntimeInfo> runtime = identities.index(state.halo().plugins());
        Map<String, Evidence> evidence = new LinkedHashMap<>();
        for (PluginResourcePersistenceSummary summary : resources.findPersistence(
            fromInclusiveMs, toExclusiveMs, MAX_OBSERVED_IDENTITIES)) {
            String key = identities.key(summary.pluginId());
            PluginRuntimeInfo plugin = runtime.get(key);
            if (plugin != null) {
                evidence.computeIfAbsent(key, ignored -> new Evidence(plugin)).resources = summary;
            }
        }
        for (PluginRequestPersistenceSummary summary : requests.findRequestPersistence(
            fromInclusiveMs, toExclusiveMs, MAX_OBSERVED_IDENTITIES)) {
            String key = identities.key(summary.pluginId());
            PluginRuntimeInfo plugin = runtime.get(key);
            if (plugin != null) {
                evidence.computeIfAbsent(key, ignored -> new Evidence(plugin)).requests = summary;
            }
        }

        long attributedCpuNs = evidence.values().stream().mapToLong(Evidence::cpuTimeNs)
            .reduce(0, PluginConsumerService::saturatedAdd);
        long attributedAllocatedBytes = evidence.values().stream()
            .mapToLong(Evidence::allocatedBytes)
            .reduce(0, PluginConsumerService::saturatedAdd);
        long totalRequestDurationUs = evidence.values().stream()
            .mapToLong(Evidence::requestDurationUs)
            .reduce(0, PluginConsumerService::saturatedAdd);
        ResourceAttributionSummary coverage = resources.summarizeCoverage(
            fromInclusiveMs, toExclusiveMs);
        long totalCpuNs = Math.max(attributedCpuNs, coverage.totalCpuTimeNs());
        long totalAllocatedBytes = Math.max(
            attributedAllocatedBytes, coverage.totalAllocatedBytes());

        List<PluginConsumerItem> ranking = new ArrayList<>(evidence.size());
        for (Evidence item : evidence.values()) {
            ranking.add(item.toItem(totalCpuNs, totalAllocatedBytes, totalRequestDurationUs,
                fromInclusiveMs, toExclusiveMs));
        }
        ranking.sort(Comparator.comparingDouble(PluginConsumerItem::pressureScore).reversed()
            .thenComparing(Comparator.comparingInt(PluginConsumerItem::topConsumerMinutes)
                .reversed())
            .thenComparing(PluginConsumerItem::pluginId));
        return List.copyOf(ranking.subList(0, Math.min(MAX_RESULTS, ranking.size())));
    }

    private static long saturatedAdd(long left, long right) {
        return right > 0 && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static double percentage(long value, long total) {
        return total <= 0 ? 0 : Math.min(100, value * 100d / total);
    }

    private static double ratio(int value, int total) {
        return total <= 0 ? 0 : Math.min(1, value / (double) total);
    }

    private static double pressure(
        double sharePercent,
        double topRatio,
        double absoluteIntensity
    ) {
        return sharePercent * (0.35 + 0.65 * topRatio) * Math.min(1, absoluteIntensity);
    }

    private static final class Evidence {

        private final PluginRuntimeInfo plugin;
        private PluginResourcePersistenceSummary resources;
        private PluginRequestPersistenceSummary requests;

        private Evidence(PluginRuntimeInfo plugin) {
            this.plugin = plugin;
        }

        private long cpuTimeNs() {
            return resources == null ? 0 : resources.cpuTimeNs();
        }

        private long allocatedBytes() {
            return resources == null ? 0 : resources.allocatedBytes();
        }

        private long requestDurationUs() {
            return requests == null ? 0 : requests.requestDurationUs();
        }

        private PluginConsumerItem toItem(
            long totalCpuNs,
            long totalAllocatedBytes,
            long totalRequestDurationUs,
            long fromMs,
            long toMs
        ) {
            int resourceMinutes = resources == null ? 0 : resources.observedMinutes();
            int requestMinutes = requests == null ? 0 : requests.observedMinutes();
            int observedMinutes = Math.max(resourceMinutes, requestMinutes);
            int resourceActiveMinutes = resources == null ? 0 : Math.max(
                Math.max(resources.cpuActiveMinutes(), resources.allocationActiveMinutes()),
                resources.threadPresentMinutes());
            int requestActiveMinutes = requests == null ? 0 : requests.activeMinutes();
            int activeMinutes = Math.max(resourceActiveMinutes, requestActiveMinutes);

            int topCpuMinutes = resources == null ? 0 : resources.topCpuMinutes();
            int topAllocationMinutes = resources == null ? 0
                : resources.topAllocationMinutes();
            int topRequestMinutes = requests == null ? 0 : requests.topDurationMinutes();
            int topMinutes = Math.max(Math.max(topCpuMinutes, topAllocationMinutes),
                topRequestMinutes);

            double cpuShare = percentage(cpuTimeNs(), totalCpuNs);
            double allocationShare = percentage(allocatedBytes(), totalAllocatedBytes);
            double requestShare = percentage(requestDurationUs(), totalRequestDurationUs);
            double cpuCorePercent = resourceMinutes == 0 ? 0
                : cpuTimeNs() * 100d / (resourceMinutes * 60_000_000_000d);
            double bytesPerMinute = resourceMinutes == 0 ? 0
                : allocatedBytes() / (double) resourceMinutes;
            double requestBusyPercent = requestMinutes == 0 ? 0
                : requestDurationUs() * 100d / (requestMinutes * 60_000_000d);
            double cpuPressure = pressure(cpuShare, ratio(topCpuMinutes, resourceMinutes),
                cpuCorePercent / 10);
            double allocationPressure = pressure(allocationShare,
                ratio(topAllocationMinutes, resourceMinutes),
                bytesPerMinute / (64 * 1_024 * 1_024d));
            double requestPressure = pressure(requestShare,
                ratio(topRequestMinutes, requestMinutes), requestBusyPercent / 25);
            double threadPressure = resources == null ? 0
                : Math.min(100, resources.liveThreadsMax() * 8d)
                    * ratio(resources.threadPresentMinutes(), resourceMinutes) * 0.35;
            double[] components = {cpuPressure, allocationPressure, requestPressure,
                threadPressure};
            double maximum = Math.max(Math.max(cpuPressure, allocationPressure),
                Math.max(requestPressure, threadPressure));
            double average = (cpuPressure + allocationPressure + requestPressure
                + threadPressure) / components.length;
            double score = Math.min(100, maximum * 0.75 + average * 0.25);
            String dominant = dominantResource(components);
            double persistencePercent = ratio(topMinutes, observedMinutes) * 100;
            boolean sustained = observedMinutes >= 3 && topMinutes >= 3
                && persistencePercent >= 30 && score >= 20;
            String confidence = observedMinutes >= 10 ? "medium"
                : observedMinutes >= 3 ? "low" : "insufficient";
            String displayName = plugin.displayName() == null || plugin.displayName().isBlank()
                ? plugin.name() : plugin.displayName();
            return new PluginConsumerItem(
                plugin.name(), displayName, plugin.enabled(), plugin.phase(), score,
                level(score), dominant, sustained, confidence, observedMinutes, activeMinutes,
                topMinutes, persistencePercent, cpuShare, allocationShare, requestShare,
                bytesPerMinute, resources == null ? 0 : resources.liveThreadsMax(),
                plugin.version(), plugin.issuesUrl(), fromMs, toMs);
        }

        private static String dominantResource(double[] components) {
            int maximumIndex = 0;
            for (int index = 1; index < components.length; index++) {
                if (components[index] > components[maximumIndex]) {
                    maximumIndex = index;
                }
            }
            return switch (maximumIndex) {
                case 0 -> "cpu";
                case 1 -> "memory-allocation";
                case 2 -> "request-time";
                default -> "threads";
            };
        }

        private static String level(double score) {
            if (score >= 60) {
                return "high";
            }
            if (score >= 30) {
                return "medium";
            }
            return score > 0 ? "low" : "none";
        }
    }
}
