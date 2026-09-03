package com.xhhao.insight.api;

import com.xhhao.insight.api.model.PluginRankingItem;
import com.xhhao.insight.halo.PluginIdentityResolver;
import com.xhhao.insight.halo.PluginRuntimeInfo;
import com.xhhao.insight.runtime.MonitoringState;
import com.xhhao.insight.resource.PluginResourceAttributionState;
import com.xhhao.insight.resource.ResourceAttributionStatus;
import com.xhhao.insight.storage.model.PluginMetricSort;
import com.xhhao.insight.storage.model.PluginMetricSummary;
import com.xhhao.insight.storage.model.PluginResourceSort;
import com.xhhao.insight.storage.model.PluginResourceSummary;
import com.xhhao.insight.storage.model.ResourceAttributionSummary;
import com.xhhao.insight.storage.repository.PluginMetricRepository;
import com.xhhao.insight.storage.repository.PluginResourceRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Resolves stored request ownership to installed plugins and builds a bounded ranking. */
@Component
public class PluginRankingService {

    private static final int MAX_OBSERVED_IDENTITIES = 2_000;
    private static final int MAX_RESULTS = 100;

    private final PluginMetricRepository repository;
    private final PluginResourceRepository resourceRepository;
    private final MonitoringState state;
    private final PluginIdentityResolver identities;
    private final PluginResourceAttributionState resourceState;

    public PluginRankingService(
        PluginMetricRepository repository,
        PluginResourceRepository resourceRepository,
        MonitoringState state,
        PluginIdentityResolver identities,
        PluginResourceAttributionState resourceState
    ) {
        this.repository = repository;
        this.resourceRepository = resourceRepository;
        this.state = state;
        this.identities = identities;
        this.resourceState = resourceState;
    }

    public List<PluginRankingItem> find(ApiTimeRange range, String sortName) {
        RankingSort sort = parseSort(sortName);
        Map<String, PluginRuntimeInfo> runtime = identities.index(state.halo().plugins());
        Map<String, Accumulator> resolved = new LinkedHashMap<>();
        for (PluginMetricSummary summary : repository.findRanking(
            range.fromMs(), range.toMs(), requestSort(sort), MAX_OBSERVED_IDENTITIES)) {
            String identityKey = identities.key(summary.pluginId());
            PluginRuntimeInfo plugin = runtime.get(identityKey);
            if (plugin != null) {
                resolved.computeIfAbsent(identityKey, ignored -> new Accumulator(plugin))
                    .addRequests(summary);
            }
        }
        for (PluginResourceSummary summary : resourceRepository.findRanking(
            range.fromMs(), range.toMs(), resourceSort(sort), MAX_OBSERVED_IDENTITIES)) {
            String identityKey = identities.key(summary.pluginId());
            PluginRuntimeInfo plugin = runtime.get(identityKey);
            if (plugin != null) {
                resolved.computeIfAbsent(identityKey, ignored -> new Accumulator(plugin))
                    .addResources(summary);
            }
        }
        ResourceAttributionSummary coverage = resourceRepository.summarizeCoverage(
            range.fromMs(), range.toMs());
        ResourceQuality quality = ResourceQuality.from(coverage, resourceState.snapshot());
        long totalDurationUs = resolved.values().stream()
            .mapToLong(Accumulator::durationSumUs)
            .reduce(0, PluginRankingService::saturatedAdd);
        List<PluginRankingItem> ranking = new ArrayList<>(resolved.size());
        for (Accumulator item : resolved.values()) {
            ranking.add(item.toItem(totalDurationUs, coverage, quality));
        }
        ranking.sort(comparator(sort));
        return List.copyOf(ranking.subList(0, Math.min(MAX_RESULTS, ranking.size())));
    }

    private static RankingSort parseSort(String value) {
        if (value == null || value.isBlank()) {
            return RankingSort.LOAD;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "errors", "error" -> RankingSort.ERRORS;
            case "latency", "average" -> RankingSort.LATENCY;
            case "load" -> RankingSort.LOAD;
            case "cpu" -> RankingSort.CPU;
            case "memory", "allocation", "allocated" -> RankingSort.MEMORY;
            case "threads", "thread" -> RankingSort.THREADS;
            default -> RankingSort.REQUESTS;
        };
    }

    private static PluginMetricSort requestSort(RankingSort sort) {
        return switch (sort) {
            case ERRORS -> PluginMetricSort.REQUEST_ERROR_COUNT;
            case LATENCY -> PluginMetricSort.AVERAGE_REQUEST_DURATION;
            case LOAD -> PluginMetricSort.REQUEST_DURATION;
            default -> PluginMetricSort.REQUEST_COUNT;
        };
    }

    private static PluginResourceSort resourceSort(RankingSort sort) {
        return switch (sort) {
            case MEMORY -> PluginResourceSort.ALLOCATED_BYTES;
            case THREADS -> PluginResourceSort.ACTIVE_THREADS;
            default -> PluginResourceSort.CPU_TIME;
        };
    }

    private static Comparator<PluginRankingItem> comparator(RankingSort sort) {
        Comparator<PluginRankingItem> metric = switch (sort) {
            case ERRORS -> Comparator.comparingLong(PluginRankingItem::errorCount);
            case LATENCY ->
                Comparator.comparingDouble(PluginRankingItem::averageResponseMs);
            case LOAD ->
                Comparator.comparingDouble(PluginRankingItem::loadSharePercent);
            case CPU -> Comparator.comparingDouble(PluginRankingItem::cpuTimeMs);
            case MEMORY -> Comparator.comparingLong(PluginRankingItem::allocatedMemoryBytes);
            case THREADS -> Comparator.comparingLong(PluginRankingItem::activeThreadSamples);
            case REQUESTS -> Comparator.comparingLong(PluginRankingItem::requestCount);
        };
        return metric.reversed().thenComparing(PluginRankingItem::pluginId);
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static final class Accumulator {

        private final PluginRuntimeInfo plugin;
        private long requestCount;
        private long errorCount;
        private long durationSumUs;
        private long durationMaxUs;
        private long cpuTimeNs;
        private long allocatedBytes;
        private long activeThreadSamples;
        private int liveThreadsMax;

        private Accumulator(PluginRuntimeInfo plugin) {
            this.plugin = plugin;
        }

        private void addRequests(PluginMetricSummary summary) {
            requestCount = saturatedAdd(requestCount, summary.requestCount());
            errorCount = saturatedAdd(errorCount, summary.requestErrorCount());
            durationSumUs = saturatedAdd(durationSumUs, summary.requestDurationSumUs());
            durationMaxUs = Math.max(durationMaxUs, summary.requestDurationMaxUs());
        }

        private void addResources(PluginResourceSummary summary) {
            cpuTimeNs = saturatedAdd(cpuTimeNs, summary.cpuTimeNs());
            allocatedBytes = saturatedAdd(allocatedBytes, summary.allocatedBytes());
            activeThreadSamples = saturatedAdd(
                activeThreadSamples, summary.activeThreadSamples());
            liveThreadsMax = Math.max(liveThreadsMax, summary.liveThreadsMax());
        }

        private long durationSumUs() {
            return durationSumUs;
        }

        private PluginRankingItem toItem(
            long totalDurationUs,
            ResourceAttributionSummary coverage,
            ResourceQuality quality
        ) {
            double loadShare = totalDurationUs == 0 ? 0
                : (double) durationSumUs / totalDurationUs * 100;
            double errorRate = requestCount == 0 ? 0 : (double) errorCount / requestCount * 100;
            double averageMs = requestCount == 0 ? 0
                : (double) durationSumUs / requestCount / 1_000;
            double cpuShare = percentage(cpuTimeNs, coverage.totalCpuTimeNs());
            double allocationShare = percentage(
                allocatedBytes, coverage.totalAllocatedBytes());
            String displayName = plugin.displayName() == null || plugin.displayName().isBlank()
                ? plugin.name() : plugin.displayName();
            return new PluginRankingItem(
                plugin.name(), displayName, plugin.enabled(), plugin.phase(), true,
                loadShare, requestCount, errorCount, errorRate,
                averageMs, durationMaxUs / 1_000d,
                quality.availability(), quality.confidence(), quality.attributionMethod(),
                quality.cpuAvailable(), quality.allocationAvailable(), cpuShare,
                allocationShare, cpuTimeNs / 1_000_000d, allocatedBytes,
                activeThreadSamples, liveThreadsMax, coverage.cpuCoveragePercent(),
                coverage.allocationCoveragePercent());
        }
    }

    private static double percentage(long value, long total) {
        return total == 0 ? 0 : Math.min(100, (double) value / total * 100);
    }

    private enum RankingSort {
        REQUESTS,
        ERRORS,
        LATENCY,
        LOAD,
        CPU,
        MEMORY,
        THREADS
    }

    private record ResourceQuality(
        String availability,
        String confidence,
        String attributionMethod,
        boolean cpuAvailable,
        boolean allocationAvailable
    ) {

        private static ResourceQuality from(
            ResourceAttributionSummary coverage,
            ResourceAttributionStatus live
        ) {
            if (coverage.sampleCount() == 0) {
                return new ResourceQuality(
                    live.availability(), live.confidence(), live.attributionMethod(),
                    live.cpuAvailable(), live.allocationAvailable());
            }
            boolean cpu = coverage.cpuCapableSamples() > 0;
            boolean allocation = coverage.allocationCapableSamples() > 0;
            boolean cpuComplete = coverage.cpuCapableSamples() == coverage.sampleCount();
            boolean allocationComplete = coverage.allocationCapableSamples()
                == coverage.sampleCount();
            String availability = cpuComplete && allocationComplete ? "AVAILABLE"
                : cpu || allocation ? "PARTIAL" : "UNSUPPORTED";
            boolean lowCoverage = (cpu && coverage.cpuCoveragePercent() < 25)
                || (allocation && coverage.allocationCoveragePercent() < 25);
            String confidence = cpu || allocation
                ? coverage.truncatedSamples() > 0 || !cpuComplete || !allocationComplete
                    || lowCoverage ? "LOW" : "MEDIUM"
                : "NONE";
            return new ResourceQuality(
                availability, confidence, live.attributionMethod(), cpu, allocation);
        }
    }
}
