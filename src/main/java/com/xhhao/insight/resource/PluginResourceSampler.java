package com.xhhao.insight.resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Samples bounded per-thread counter deltas and attributes them only across a stable, exact
 * plugin context class loader. It deliberately leaves shared-pool and core threads unattributed.
 */
@Slf4j
@Component
public class PluginResourceSampler {

    static final int MINIMUM_THREADS = 32;
    static final int MAXIMUM_THREADS = 2_048;

    private final ThreadInventory threads;
    private final ThreadResourceReader resources;
    private final PluginClassLoaderIndex pluginClassLoaders;
    private final PluginResourceAttributionState state;
    private Map<Long, Baseline> previous = Map.of();
    private boolean baselined;

    @Autowired
    public PluginResourceSampler(
        PluginClassLoaderIndex pluginClassLoaders,
        PluginResourceAttributionState state
    ) {
        this(new BoundedThreadInventory(), new JmxThreadResourceReader(),
            pluginClassLoaders, state);
    }

    PluginResourceSampler(
        ThreadInventory threads,
        ThreadResourceReader resources,
        PluginClassLoaderIndex pluginClassLoaders,
        PluginResourceAttributionState state
    ) {
        this.threads = threads;
        this.resources = resources;
        this.pluginClassLoaders = pluginClassLoaders;
        this.state = state;
    }

    public synchronized void start(long startedAtMs) {
        resetBaseline();
        state.warming(false, false, startedAtMs);
    }

    public synchronized void stop(long stoppedAtMs) {
        resetBaseline();
        state.disabled(stoppedAtMs);
    }

    public synchronized Optional<ResourceAttributionSample> sample(
        long sampledAtMs,
        boolean enabled,
        int configuredMaximumThreads
    ) {
        if (!enabled) {
            resetBaseline();
            state.disabled(sampledAtMs);
            return Optional.empty();
        }
        int maximumThreads = Math.max(MINIMUM_THREADS,
            Math.min(MAXIMUM_THREADS, configuredMaximumThreads));
        try {
            ThreadInventory.ThreadScan scan = threads.scan(maximumThreads);
            List<Thread> currentThreads = scan.threads();
            long[] threadIds = currentThreads.stream().mapToLong(Thread::threadId).toArray();
            ThreadResourceReader.ThreadResourceSnapshot counters = resources.read(threadIds);
            if (!counters.cpuAvailable() && !counters.allocationAvailable()) {
                resetBaseline();
                state.unsupported(sampledAtMs);
                return Optional.empty();
            }

            Map<ClassLoader, String> owners = pluginClassLoaders.snapshot();
            Map<Long, Baseline> current = new HashMap<>(currentThreads.size());
            Map<String, Integer> liveThreads = new HashMap<>();
            int attributedThreads = 0;
            for (int index = 0; index < currentThreads.size(); index++) {
                Thread thread = currentThreads.get(index);
                PluginClassLoaderIndex.PluginThreadOwner owner =
                    PluginClassLoaderIndex.owner(thread, owners).orElse(null);
                if (owner != null) {
                    liveThreads.merge(owner.pluginId(), 1, Integer::sum);
                    attributedThreads++;
                }
                current.put(thread.threadId(), new Baseline(
                    thread, owner == null ? null : owner.pluginId(),
                    owner == null ? null : owner.classLoader(),
                    value(counters.cpuTimeNs(), index),
                    value(counters.allocatedBytes(), index)));
            }
            if (!baselined) {
                previous = current;
                baselined = true;
                state.warming(counters.cpuAvailable(), counters.allocationAvailable(), sampledAtMs);
                return Optional.empty();
            }

            long totalCpuTimeNs = 0;
            long attributedCpuTimeNs = 0;
            long totalAllocatedBytes = 0;
            long attributedAllocatedBytes = 0;
            Map<String, MutableDelta> deltas = new LinkedHashMap<>();
            for (Baseline currentValue : current.values()) {
                Baseline oldValue = previous.get(currentValue.thread().threadId());
                if (oldValue == null || oldValue.thread() != currentValue.thread()) {
                    continue;
                }
                long cpuDelta = delta(oldValue.cpuTimeNs(), currentValue.cpuTimeNs());
                long allocationDelta = delta(
                    oldValue.allocatedBytes(), currentValue.allocatedBytes());
                if (cpuDelta >= 0) {
                    totalCpuTimeNs = saturatedAdd(totalCpuTimeNs, cpuDelta);
                }
                if (allocationDelta >= 0) {
                    totalAllocatedBytes = saturatedAdd(totalAllocatedBytes, allocationDelta);
                }
                if (currentValue.pluginId() == null
                    || !currentValue.pluginId().equals(oldValue.pluginId())
                    || currentValue.pluginClassLoader() != oldValue.pluginClassLoader()) {
                    continue;
                }
                MutableDelta plugin = deltas.computeIfAbsent(
                    currentValue.pluginId(), ignored -> new MutableDelta());
                if (cpuDelta >= 0) {
                    plugin.cpuMeasurementSamples++;
                    plugin.cpuTimeNs = saturatedAdd(plugin.cpuTimeNs, cpuDelta);
                    attributedCpuTimeNs = saturatedAdd(attributedCpuTimeNs, cpuDelta);
                    if (cpuDelta > 0) {
                        plugin.cpuActiveSamples++;
                    }
                }
                if (allocationDelta >= 0) {
                    plugin.allocationMeasurementSamples++;
                    plugin.allocatedBytes = saturatedAdd(plugin.allocatedBytes, allocationDelta);
                    attributedAllocatedBytes = saturatedAdd(
                        attributedAllocatedBytes, allocationDelta);
                }
                if (cpuDelta > 0 || allocationDelta > 0) {
                    plugin.activeThreadSamples++;
                }
            }
            liveThreads.forEach((pluginId, count) ->
                deltas.computeIfAbsent(pluginId, ignored -> new MutableDelta()).liveThreads = count);
            List<PluginResourceDelta> pluginDeltas = new ArrayList<>(deltas.size());
            deltas.forEach((pluginId, value) -> pluginDeltas.add(value.toDelta(pluginId)));

            ResourceAttributionSample sample = new ResourceAttributionSample(
                sampledAtMs, counters.cpuAvailable(), counters.allocationAvailable(),
                scan.truncated(), currentThreads.size(), attributedThreads,
                totalCpuTimeNs, attributedCpuTimeNs, totalAllocatedBytes,
                attributedAllocatedBytes, List.copyOf(pluginDeltas));
            previous = current;
            state.available(sample);
            return Optional.of(sample);
        } catch (RuntimeException | LinkageError failure) {
            resetBaseline();
            if (state.failed(sampledAtMs)) {
                log.warn("Plugin resource attribution sampling is unavailable ({})",
                    failure.getClass().getSimpleName());
            }
            return Optional.empty();
        }
    }

    private void resetBaseline() {
        previous = Map.of();
        baselined = false;
    }

    private static long value(long[] values, int index) {
        return index < values.length ? values[index] : -1;
    }

    private static long delta(long previous, long current) {
        return previous < 0 || current < previous ? -1 : current - previous;
    }

    private static long saturatedAdd(long left, long right) {
        return right > 0 && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private record Baseline(
        Thread thread,
        String pluginId,
        ClassLoader pluginClassLoader,
        long cpuTimeNs,
        long allocatedBytes
    ) {
    }

    private static final class MutableDelta {

        private long cpuMeasurementSamples;
        private long allocationMeasurementSamples;
        private long cpuActiveSamples;
        private long activeThreadSamples;
        private int liveThreads;
        private long cpuTimeNs;
        private long allocatedBytes;

        private PluginResourceDelta toDelta(String pluginId) {
            return new PluginResourceDelta(
                pluginId, cpuMeasurementSamples, allocationMeasurementSamples,
                cpuActiveSamples, activeThreadSamples, liveThreads, cpuTimeNs, allocatedBytes);
        }
    }
}
