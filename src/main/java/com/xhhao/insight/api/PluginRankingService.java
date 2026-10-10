package com.xhhao.insight.api;

import com.xhhao.insight.api.model.PluginRankingItem;
import com.xhhao.insight.halo.PluginIdentityResolver;
import com.xhhao.insight.halo.PluginRuntimeInfo;
import com.xhhao.insight.resource.PluginResourceAttributionState;
import com.xhhao.insight.resource.PluginResourceDelta;
import com.xhhao.insight.resource.ResourceAttributionSample;
import com.xhhao.insight.resource.ResourceAttributionStatus;
import com.xhhao.insight.runtime.MonitoringState;
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
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Resolves stored request ownership to installed plugins and builds a complete ranking. */
@Component
public class PluginRankingService {

    private static final int MAX_OBSERVED_IDENTITIES = 2_000;
    private static final long MIN_LIVE_SAMPLE_AGE_MS = 30_000;

    private final PluginMetricRepository repository;
    private final PluginResourceRepository resourceRepository;
    private final MonitoringState state;
    private final PluginIdentityResolver identities;
    private final PluginResourceAttributionState resourceState;
    private final Clock clock;

    @Autowired
    public PluginRankingService(
        PluginMetricRepository repository,
        PluginResourceRepository resourceRepository,
        MonitoringState state,
        PluginIdentityResolver identities,
        PluginResourceAttributionState resourceState
    ) {
        this(repository, resourceRepository, state, identities, resourceState, Clock.systemUTC());
    }

    PluginRankingService(
        PluginMetricRepository repository,
        PluginResourceRepository resourceRepository,
        MonitoringState state,
        PluginIdentityResolver identities,
        PluginResourceAttributionState resourceState,
        Clock clock
    ) {
        this.repository = repository;
        this.resourceRepository = resourceRepository;
        this.state = state;
        this.identities = identities;
        this.resourceState = resourceState;
        this.clock = clock;
    }

    public List<PluginRankingItem> find(ApiTimeRange range, String sortName) {
        return find(range, sortName, true);
    }

    public List<PluginRankingItem> find(
        ApiTimeRange range,
        String sortName,
        boolean historyAvailable
    ) {
        RankingSort sort = parseSort(sortName);
        Map<String, PluginRuntimeInfo> runtime = identities.index(state.halo().plugins());
        Map<String, Accumulator> resolved = new LinkedHashMap<>();
        // Keep every currently known plugin in the result, including plugins that have no
        // request or resource samples in the selected range. This makes the table useful as a
        // complete plugin inventory instead of only showing active consumers.
        runtime.forEach((identityKey, plugin) ->
            resolved.put(identityKey, new Accumulator(plugin)));
        if (historyAvailable) {
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
        }
        ResourceAttributionSummary coverage = historyAvailable
            ? resourceRepository.summarizeCoverage(range.fromMs(), range.toMs())
            : ResourceAttributionSummary.empty();
        ResourceQuality quality = ResourceQuality.from(coverage, resourceState.snapshot());
        ResourceAttributionSample liveSample = freshSample(resourceState.latestSample());
        Map<String, PluginResourceDelta> liveDeltas = liveDeltas(liveSample);
        long totalDurationUs = resolved.values().stream()
            .mapToLong(Accumulator::durationSumUs)
            .reduce(0, PluginRankingService::saturatedAdd);
        List<PluginRankingItem> ranking = new ArrayList<>(resolved.size());
        for (Accumulator item : resolved.values()) {
            ranking.add(item.toItem(totalDurationUs, coverage, quality, liveSample,
                liveDeltas.get(identities.key(item.plugin.name()))));
        }
        ranking.sort(comparator(sort, liveSample));
        return List.copyOf(ranking);
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

    private static Comparator<PluginRankingItem> comparator(
        RankingSort sort,
        ResourceAttributionSample liveSample
    ) {
        boolean liveCpu = liveSample != null && liveSample.cpuAvailable();
        boolean liveAllocation = liveSample != null && liveSample.allocationAvailable();
        boolean liveThreads = liveSample != null;
        Comparator<PluginRankingItem> metric = switch (sort) {
            case ERRORS -> Comparator.comparingLong(PluginRankingItem::errorCount);
            case LATENCY ->
                Comparator.comparingDouble(PluginRankingItem::averageResponseMs);
            case LOAD ->
                Comparator.comparingDouble(PluginRankingItem::loadSharePercent);
            case CPU -> liveCpu
                ? Comparator.comparingDouble(item -> item.liveCpuAvailable()
                    ? item.liveCpuPercent() : -1)
                : Comparator.comparingDouble(item -> item.cpuTimeAvailable()
                    ? item.cpuTimeMs() : -1);
            case MEMORY -> liveAllocation
                ? Comparator.comparingLong(item -> item.liveAllocationAvailable()
                    ? item.liveAllocationBytesPerSecond() : -1)
                : Comparator.comparingLong(item -> item.allocatedMemoryAvailable()
                    ? item.allocatedMemoryBytes() : -1);
            case THREADS -> liveThreads
                ? Comparator.comparingInt(item -> item.liveThreadsAvailable()
                    ? item.liveThreads() : -1)
                : Comparator.comparingLong(PluginRankingItem::activeThreadSamples);
            case REQUESTS -> Comparator.comparingLong(PluginRankingItem::requestCount);
        };
        return metric.reversed().thenComparing(PluginRankingItem::pluginId);
    }

    private static Map<String, PluginResourceDelta> liveDeltas(
        ResourceAttributionSample sample
    ) {
        if (sample == null) {
            return Map.of();
        }
        Map<String, PluginResourceDelta> result = new LinkedHashMap<>();
        for (PluginResourceDelta delta : sample.plugins()) {
            if (delta.pluginId() != null && !delta.pluginId().isBlank()) {
                result.put(delta.pluginId().toLowerCase(Locale.ROOT), delta);
            }
        }
        return result;
    }

    private ResourceAttributionSample freshSample(ResourceAttributionSample sample) {
        if (sample == null || sample.intervalMs() <= 0 || sample.sampledAtMs() <= 0) {
            return null;
        }
        long age = clock.millis() - sample.sampledAtMs();
        long maxAge = Math.max(MIN_LIVE_SAMPLE_AGE_MS,
            saturatedAdd(sample.intervalMs(), sample.intervalMs()));
        return age >= 0 && age <= maxAge ? sample : null;
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
        private long cpuMeasurementSamples;
        private long allocationMeasurementSamples;
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
            cpuMeasurementSamples = saturatedAdd(cpuMeasurementSamples,
                summary.cpuMeasurementSamples());
            allocationMeasurementSamples = saturatedAdd(allocationMeasurementSamples,
                summary.allocationMeasurementSamples());
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
            ResourceQuality quality,
            ResourceAttributionSample liveSample,
            PluginResourceDelta liveDelta
        ) {
            if (!plugin.enabled() || !"STARTED".equalsIgnoreCase(plugin.phase())) {
                liveDelta = null;
            }
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
            boolean liveIntervalAvailable = liveSample != null;
            boolean liveCpuAvailable = liveIntervalAvailable && liveSample.cpuAvailable()
                && liveDelta != null && liveDelta.cpuMeasurementSamples() > 0;
            boolean liveAllocationAvailable = liveIntervalAvailable
                && liveSample.allocationAvailable() && liveDelta != null
                && liveDelta.allocationMeasurementSamples() > 0;
            double liveCpuPercent = liveCpuAvailable && liveDelta != null
                ? cpuPercent(liveDelta.cpuTimeNs(), liveSample.intervalMs()) : 0;
            long liveAllocationBytesPerSecond = liveAllocationAvailable && liveDelta != null
                ? bytesPerSecond(liveDelta.allocatedBytes(), liveSample.intervalMs()) : 0;
            int liveThreads = liveDelta == null ? 0 : liveDelta.liveThreads();
            return new PluginRankingItem(
                plugin.name(), displayName, plugin.enabled(), plugin.phase(), true,
                loadShare, requestCount, errorCount, errorRate,
                averageMs, durationMaxUs / 1_000d,
                quality.availability(), quality.confidence(), quality.attributionMethod(),
                quality.cpuAvailable() && cpuMeasurementSamples > 0,
                quality.allocationAvailable() && allocationMeasurementSamples > 0, cpuShare,
                allocationShare, cpuTimeNs / 1_000_000d, allocatedBytes,
                activeThreadSamples, liveThreadsMax, coverage.cpuCoveragePercent(),
                coverage.allocationCoveragePercent(), liveCpuAvailable,
                liveAllocationAvailable, liveCpuPercent, liveAllocationBytesPerSecond,
                liveIntervalAvailable && liveDelta != null, liveThreads,
                liveIntervalAvailable ? liveSample.sampledAtMs() : 0,
                liveIntervalAvailable ? liveSample.intervalMs() : 0);
        }
    }

    private static double percentage(long value, long total) {
        return total == 0 ? 0 : Math.min(100, (double) value / total * 100);
    }

    private static double cpuPercent(long cpuTimeNs, long intervalMs) {
        return intervalMs <= 0 ? 0 : (double) cpuTimeNs / (intervalMs * 1_000_000d) * 100;
    }

    private static long bytesPerSecond(long bytes, long intervalMs) {
        if (intervalMs <= 0 || bytes <= 0) {
            return 0;
        }
        double rate = (double) bytes * 1_000 / intervalMs;
        return rate >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) rate;
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
